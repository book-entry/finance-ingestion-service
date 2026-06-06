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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobProcessorTest {

    private static final String USER_ID = "user-bulk";

    @Mock BulkUploadJobRepository jobRepository;
    @Mock AccountValidationClient accountValidationClient;
    @Mock TransactionBatchClient transactionBatchClient;

    JobProcessor processor;

    @BeforeEach
    void setUp() {
        BulkUploadProperties props = new BulkUploadProperties();
        props.getBulkUpload().setBatchSize(2);
        processor = new JobProcessor(jobRepository, accountValidationClient, transactionBatchClient, props);
    }

    @Test
    void process_happyPath_thenCompletesWithSuccessCount() {
        UUID jobId = UUID.randomUUID();
        BulkUploadJob job = jobOf(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID account = UUID.randomUUID();
        when(accountValidationClient.validate(eq(USER_ID), any()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(List.of(account)).invalid(List.of()).build());
        // Mock reflects the real chunk size so insertedCount accumulates
        // correctly across the two chunks (batchSize=2, 3 rows -> 2 + 1).
        when(transactionBatchClient.sendBatch(eq(USER_ID), any())).thenAnswer(inv -> {
            BatchRequestPayload payload = inv.getArgument(1);
            return BatchInsertResult.builder()
                    .insertedCount(payload.getRows().size())
                    .failedRows(List.of()).build();
        });

        List<CsvTransactionRow> rows = List.of(row(1, account), row(2, account), row(3, account));
        processor.process(jobId, USER_ID, rows, List.of());

        ArgumentCaptor<BulkUploadJob> captor = ArgumentCaptor.forClass(BulkUploadJob.class);
        verify(jobRepository, atLeastOnce()).save(captor.capture());
        BulkUploadJob finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(finalState.getSuccessCount()).isEqualTo(3);
        assertThat(finalState.getErrorCount()).isZero();
        assertThat(finalState.getErrorDetail()).isNull();
    }

    @Test
    void process_chunksRowsAccordingToBatchSize() {
        UUID jobId = UUID.randomUUID();
        BulkUploadJob job = jobOf(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID account = UUID.randomUUID();
        when(accountValidationClient.validate(eq(USER_ID), any()))
                .thenReturn(AccountValidationResponse.builder().valid(List.of(account)).invalid(List.of()).build());
        when(transactionBatchClient.sendBatch(eq(USER_ID), any()))
                .thenReturn(BatchInsertResult.builder().insertedCount(2).failedRows(List.of()).build());

        // batchSize=2 → 5 rows should produce 3 chunks (2, 2, 1).
        List<CsvTransactionRow> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) rows.add(row(i, account));
        processor.process(jobId, USER_ID, rows, List.of());

        ArgumentCaptor<BatchRequestPayload> captor = ArgumentCaptor.forClass(BatchRequestPayload.class);
        verify(transactionBatchClient, atLeastOnce()).sendBatch(eq(USER_ID), captor.capture());
        assertThat(captor.getAllValues()).hasSize(3);
        assertThat(captor.getAllValues().get(0).getRows()).hasSize(2);
        assertThat(captor.getAllValues().get(2).getRows()).hasSize(1);
    }

    @Test
    void process_rowsWithInvalidAccount_thenRecordedAsErrors() {
        UUID jobId = UUID.randomUUID();
        BulkUploadJob job = jobOf(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID good = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        when(accountValidationClient.validate(eq(USER_ID), any()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(List.of(good)).invalid(List.of(bad)).build());
        when(transactionBatchClient.sendBatch(eq(USER_ID), any()))
                .thenReturn(BatchInsertResult.builder().insertedCount(1).failedRows(List.of()).build());

        List<CsvTransactionRow> rows = List.of(row(1, good), row(2, bad));
        processor.process(jobId, USER_ID, rows, List.of());

        ArgumentCaptor<BulkUploadJob> captor = ArgumentCaptor.forClass(BulkUploadJob.class);
        verify(jobRepository, atLeastOnce()).save(captor.capture());
        BulkUploadJob finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalState.getErrorCount()).isEqualTo(1);
        assertThat(finalState.getErrorDetail()).extracting(ErrorRow::getReason)
                .anyMatch(r -> r.contains("invalid"));
    }

    @Test
    void process_givenAccountValidationFails_thenJobFailedAndCallFlagged() {
        UUID jobId = UUID.randomUUID();
        BulkUploadJob job = jobOf(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountValidationClient.validate(eq(USER_ID), any()))
                .thenThrow(new RuntimeException("upstream down"));

        processor.process(jobId, USER_ID, List.of(row(1, UUID.randomUUID())), List.of());

        ArgumentCaptor<BulkUploadJob> captor = ArgumentCaptor.forClass(BulkUploadJob.class);
        verify(jobRepository, atLeastOnce()).save(captor.capture());
        BulkUploadJob finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalState.getErrorDetail()).extracting(ErrorRow::getReason)
                .anyMatch(r -> r.contains("Account validation"));
    }

    @Test
    void process_givenBatchCallFailsForOneChunk_thenChunkRowsMarkedFailed() {
        UUID jobId = UUID.randomUUID();
        BulkUploadJob job = jobOf(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID account = UUID.randomUUID();
        when(accountValidationClient.validate(eq(USER_ID), any()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(List.of(account)).invalid(List.of()).build());
        when(transactionBatchClient.sendBatch(eq(USER_ID), any()))
                .thenThrow(new RuntimeException("downstream 500"));

        processor.process(jobId, USER_ID, List.of(row(1, account), row(2, account)), List.of());

        ArgumentCaptor<BulkUploadJob> captor = ArgumentCaptor.forClass(BulkUploadJob.class);
        verify(jobRepository, atLeastOnce()).save(captor.capture());
        BulkUploadJob finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalState.getErrorCount()).isEqualTo(2);
    }

    @Test
    void process_forwardsCategoryNameFromCsvRowIntoBatchPayload() {
        UUID jobId = UUID.randomUUID();
        BulkUploadJob job = jobOf(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID account = UUID.randomUUID();
        when(accountValidationClient.validate(eq(USER_ID), any()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(List.of(account)).invalid(List.of()).build());
        when(transactionBatchClient.sendBatch(eq(USER_ID), any()))
                .thenReturn(BatchInsertResult.builder().insertedCount(2).failedRows(List.of()).build());

        CsvTransactionRow named = CsvTransactionRow.builder()
                .rowIndex(1).accountId(account).entryType("DEBIT")
                .amount(new BigDecimal("1.00")).currency("USD")
                .transactionDate(LocalDate.of(2026, 5, 24))
                .categoryName("Food").build();
        CsvTransactionRow blank = row(2, account); // categoryName left null
        processor.process(jobId, USER_ID, List.of(named, blank), List.of());

        ArgumentCaptor<BatchRequestPayload> captor = ArgumentCaptor.forClass(BatchRequestPayload.class);
        verify(transactionBatchClient, atLeastOnce()).sendBatch(eq(USER_ID), captor.capture());
        BatchRequestPayload sent = captor.getValue();
        assertThat(sent.getRows()).hasSize(2);
        assertThat(sent.getRows().get(0).getCategoryName()).isEqualTo("Food");
        assertThat(sent.getRows().get(1).getCategoryName()).isNull();
    }

    @Test
    void process_givenParseErrorsAndSomeSuccesses_thenStatusCompletedNotFailed() {
        UUID jobId = UUID.randomUUID();
        BulkUploadJob job = jobOf(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID account = UUID.randomUUID();
        when(accountValidationClient.validate(eq(USER_ID), any()))
                .thenReturn(AccountValidationResponse.builder()
                        .valid(List.of(account)).invalid(List.of()).build());
        when(transactionBatchClient.sendBatch(eq(USER_ID), any()))
                .thenReturn(BatchInsertResult.builder().insertedCount(1).failedRows(List.of()).build());

        List<ErrorRow> parseErrors = List.of(ErrorRow.builder().row(2).reason("bad row").build());
        processor.process(jobId, USER_ID, List.of(row(1, account)), parseErrors);

        ArgumentCaptor<BulkUploadJob> captor = ArgumentCaptor.forClass(BulkUploadJob.class);
        verify(jobRepository, atLeastOnce()).save(captor.capture());
        BulkUploadJob finalState = captor.getAllValues().get(captor.getAllValues().size() - 1);
        // Spec §3.4: "COMPLETED — All valid rows processed. May have partial errors."
        assertThat(finalState.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(finalState.getSuccessCount()).isEqualTo(1);
        assertThat(finalState.getErrorCount()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private BulkUploadJob jobOf(UUID id) {
        return BulkUploadJob.builder()
                .jobId(id).userId(USER_ID).status(JobStatus.PENDING)
                .successCount(0).errorCount(0).build();
    }

    private CsvTransactionRow row(int idx, UUID accountId) {
        return CsvTransactionRow.builder()
                .rowIndex(idx)
                .accountId(accountId)
                .entryType("DEBIT")
                .amount(new BigDecimal("1.00"))
                .currency("USD")
                .transactionDate(LocalDate.of(2026, 5, 24))
                .build();
    }
}
