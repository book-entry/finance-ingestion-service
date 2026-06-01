package com.personal.finance.bulkupload.service;

import com.personal.finance.bulkupload.dto.response.JobStatusResponse;
import com.personal.finance.bulkupload.dto.response.UploadAcceptedResponse;
import com.personal.finance.bulkupload.entity.BulkUploadJob;
import com.personal.finance.bulkupload.enums.JobStatus;
import com.personal.finance.bulkupload.exception.InvalidCsvException;
import com.personal.finance.bulkupload.exception.JobNotFoundException;
import com.personal.finance.bulkupload.repository.BulkUploadJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkUploadServiceImpl implements BulkUploadService {

    private final BulkUploadJobRepository jobRepository;
    private final CsvParser csvParser;
    private final JobProcessor jobProcessor;

    /**
     * Spec §3.4 POST — High-Level Logic:
     * <ol>
     *   <li>400 if file missing or unreadable.</li>
     *   <li>Create job row with status=PENDING; return 202 {jobId}.</li>
     *   <li>Parse CSV synchronously (so headers can be validated before 202);
     *       handoff to async processor for upstream calls.</li>
     * </ol>
     */
    @Override
    @Transactional
    public UploadAcceptedResponse submitUpload(String userId, MultipartFile file) {
        assertCsv(file);

        BulkUploadJob job = BulkUploadJob.builder()
                .userId(userId)
                .fileName(file.getOriginalFilename())
                .status(JobStatus.PENDING)
                .successCount(0)
                .errorCount(0)
                .build();
        BulkUploadJob saved = jobRepository.save(job);
        log.info("Bulk job created uid=[{}] id=[{}] file=[{}]",
                userId, saved.getJobId(), saved.getFileName());

        CsvParser.ParseResult parsed;
        try {
            parsed = csvParser.parse(file.getInputStream());
        } catch (IOException ex) {
            throw new InvalidCsvException("Failed to read CSV stream: " + ex.getMessage(), ex);
        }

        // Hand off to async pipeline. The proxy bound to JobProcessor switches
        // to a Spring task-executor thread; this method returns immediately.
        jobProcessor.process(saved.getJobId(), userId, parsed.getRows(), parsed.getErrors());
        return UploadAcceptedResponse.builder().jobId(saved.getJobId()).build();
    }

    /** Spec §3.4 GET — 404 if job missing or owned by a different user. */
    @Override
    @Transactional(readOnly = true)
    public JobStatusResponse getJobStatus(String userId, UUID jobId) {
        BulkUploadJob job = jobRepository.findByJobIdAndUserId(jobId, userId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return JobStatusResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .totalRows(job.getTotalRows())
                .successCount(job.getSuccessCount())
                .errorCount(job.getErrorCount())
                .errorRows(job.getErrorDetail())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }

    private static void assertCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidCsvException("File is missing or empty");
        }
        String name = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean csvByName = name != null && name.toLowerCase().endsWith(".csv");
        boolean csvByContent = contentType != null && (
                contentType.equalsIgnoreCase("text/csv")
                        || contentType.equalsIgnoreCase("application/csv")
                        || contentType.equalsIgnoreCase("application/vnd.ms-excel")
                        || contentType.equalsIgnoreCase("application/octet-stream"));
        if (!csvByName && !csvByContent) {
            throw new InvalidCsvException("File must be a CSV (filename or content-type)");
        }
    }
}
