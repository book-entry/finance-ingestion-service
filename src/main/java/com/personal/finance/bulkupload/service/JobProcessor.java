package com.personal.finance.bulkupload.service;

import com.personal.finance.bulkupload.client.account.AccountValidationClient;
import com.personal.finance.bulkupload.client.account.AccountValidationResponse;
import com.personal.finance.bulkupload.client.transaction.BatchInsertResult;
import com.personal.finance.bulkupload.client.transaction.BatchRequestPayload;
import com.personal.finance.bulkupload.client.transaction.TransactionBatchClient;
import com.personal.finance.bulkupload.config.BulkUploadProperties;
import com.personal.finance.bulkupload.dto.ErrorRow;
import com.personal.finance.bulkupload.dto.internal.CsvTransactionRow;
import com.personal.finance.bulkupload.entity.BulkUploadJob;
import com.personal.finance.bulkupload.enums.JobStatus;
import com.personal.finance.bulkupload.repository.BulkUploadJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Async pipeline for spec §3.4 step 3 onwards: PROCESSING → validate
 * accountIds → batch-insert in chunks → COMPLETED / FAILED.
 *
 * <p>Lives in its own bean so the {@code @Async} proxy works (Spring wraps
 * the method only when called from outside the same bean).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobProcessor {

    private final BulkUploadJobRepository jobRepository;
    private final AccountValidationClient accountValidationClient;
    private final TransactionBatchClient transactionBatchClient;
    private final BulkUploadProperties properties;

    /**
     * Runs on a Spring-managed task executor. Idempotent under retry only if
     * the caller resets the row to PENDING first.
     */
    @Async
    @Transactional
    public void process(UUID jobId, String userId, List<CsvTransactionRow> rows, List<ErrorRow> parseErrors) {
        log.info("Processing job=[{}] uid=[{}] rows=[{}] parseErrors=[{}]",
                jobId, userId, rows.size(), parseErrors.size());

        BulkUploadJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.PROCESSING);
        job.setTotalRows(rows.size() + parseErrors.size());
        jobRepository.save(job);

        List<ErrorRow> errors = new ArrayList<>(parseErrors);

        // Step 4 — batch validate accountIds.
        Set<UUID> distinctAccountIds = rows.stream().map(CsvTransactionRow::getAccountId).collect(Collectors.toSet());
        Set<UUID> invalidAccounts;
        try {
            AccountValidationResponse validation = accountValidationClient.validate(userId, distinctAccountIds);
            invalidAccounts = new HashSet<>(validation.getInvalid() == null ? List.of() : validation.getInvalid());
        } catch (Exception ex) {
            log.error("Account validation failed entirely for job={}", jobId, ex);
            failJob(job, errors, "Account validation call failed: " + ex.getMessage());
            return;
        }

        List<CsvTransactionRow> valid = new ArrayList<>();
        for (CsvTransactionRow row : rows) {
            if (invalidAccounts.contains(row.getAccountId())) {
                errors.add(ErrorRow.builder()
                        .row(row.getRowIndex())
                        .reason("accountId " + row.getAccountId() + " is invalid for this user")
                        .build());
            } else {
                valid.add(row);
            }
        }

        // Step 5/6 — chunked POST to Transaction Service.
        int successCount = 0;
        int batchSize = Math.max(properties.getBulkUpload().getBatchSize(), 1);
        for (int from = 0; from < valid.size(); from += batchSize) {
            List<CsvTransactionRow> chunk = valid.subList(from, Math.min(from + batchSize, valid.size()));
            BatchRequestPayload payload = buildPayload(jobId, chunk);
            try {
                BatchInsertResult result = transactionBatchClient.sendBatch(userId, payload);
                successCount += result.getInsertedCount();
                if (result.getFailedRows() != null) {
                    for (BatchInsertResult.FailedRow failed : result.getFailedRows()) {
                        int chunkLocalIdx = failed.getRowIndex();
                        if (chunkLocalIdx >= 0 && chunkLocalIdx < chunk.size()) {
                            errors.add(ErrorRow.builder()
                                    .row(chunk.get(chunkLocalIdx).getRowIndex())
                                    .reason(failed.getReason())
                                    .build());
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Batch call failed for chunk starting at row {} of job {}", from, jobId, ex);
                for (CsvTransactionRow lost : chunk) {
                    errors.add(ErrorRow.builder()
                            .row(lost.getRowIndex())
                            .reason("Batch call failed: " + ex.getMessage())
                            .build());
                }
            }
        }

        // Step 7 — finalise.
        job.setSuccessCount(successCount);
        job.setErrorCount(errors.size());
        job.setErrorDetail(errors.isEmpty() ? null : errors);
        job.setStatus(successCount == 0 && !errors.isEmpty() ? JobStatus.FAILED : JobStatus.COMPLETED);
        job.setCompletedAt(OffsetDateTime.now());
        jobRepository.save(job);
        log.info("Job [{}] finished status=[{}] success=[{}] failed=[{}]",
                jobId, job.getStatus(), successCount, errors.size());
    }

    private void failJob(BulkUploadJob job, List<ErrorRow> errors, String reason) {
        errors.add(ErrorRow.builder().row(0).reason(reason).build());
        job.setSuccessCount(0);
        job.setErrorCount(errors.size());
        job.setErrorDetail(errors);
        job.setStatus(JobStatus.FAILED);
        job.setCompletedAt(OffsetDateTime.now());
        jobRepository.save(job);
    }

    private BatchRequestPayload buildPayload(UUID jobId, List<CsvTransactionRow> chunk) {
        List<BatchRequestPayload.Row> rows = chunk.stream()
                .map(c -> BatchRequestPayload.Row.builder()
                        .accountId(c.getAccountId())
                        .entryType(c.getEntryType())
                        .amount(c.getAmount())
                        .currency(c.getCurrency())
                        .transactionDate(c.getTransactionDate())
                        .reference(c.getReference())
                        .description(c.getDescription())
                        .categoryName(c.getCategoryName())
                        .build())
                .toList();
        return BatchRequestPayload.builder().bulkJobId(jobId).rows(rows).build();
    }
}
