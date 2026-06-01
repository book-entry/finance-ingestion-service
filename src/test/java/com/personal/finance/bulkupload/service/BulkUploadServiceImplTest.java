package com.personal.finance.bulkupload.service;

import com.personal.finance.bulkupload.dto.ErrorRow;
import com.personal.finance.bulkupload.dto.response.JobStatusResponse;
import com.personal.finance.bulkupload.dto.response.UploadAcceptedResponse;
import com.personal.finance.bulkupload.entity.BulkUploadJob;
import com.personal.finance.bulkupload.enums.JobStatus;
import com.personal.finance.bulkupload.exception.InvalidCsvException;
import com.personal.finance.bulkupload.exception.JobNotFoundException;
import com.personal.finance.bulkupload.repository.BulkUploadJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkUploadServiceImplTest {

    private static final String USER_ID = "user-bulk";

    @Mock BulkUploadJobRepository repository;
    @Mock JobProcessor jobProcessor;

    BulkUploadServiceImpl service;

    @BeforeEach
    void setUp() {
        // Use the real CsvParser — parsing logic is unit tested separately.
        service = new BulkUploadServiceImpl(repository, new CsvParser(), jobProcessor);
    }

    @Test
    void submitUpload_givenValidCsv_thenCreatesJobAndKicksOffAsync() {
        MultipartFile file = csvFile("ok.csv",
                "accountId,entryType,amount,currency,transactionDate\n"
                        + "a4d4f7c8-1111-1111-1111-111111111111,DEBIT,1.00,USD,2026-05-24\n");
        when(repository.save(any(BulkUploadJob.class))).thenAnswer(inv -> {
            BulkUploadJob j = inv.getArgument(0);
            j.setJobId(UUID.randomUUID());
            return j;
        });

        UploadAcceptedResponse resp = service.submitUpload(USER_ID, file);

        assertThat(resp.getJobId()).isNotNull();
        verify(jobProcessor).process(any(), anyString(), any(), any());
    }

    @Test
    void submitUpload_givenNullFile_thenThrowsInvalidCsv() {
        assertThatThrownBy(() -> service.submitUpload(USER_ID, null))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void submitUpload_givenEmptyFile_thenThrowsInvalidCsv() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        assertThatThrownBy(() -> service.submitUpload(USER_ID, empty))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void submitUpload_givenNonCsvFile_thenThrowsInvalidCsv() {
        MockMultipartFile pdf = new MockMultipartFile("file", "report.pdf", "application/pdf", "x".getBytes());
        assertThatThrownBy(() -> service.submitUpload(USER_ID, pdf))
                .isInstanceOf(InvalidCsvException.class);
        verify(jobProcessor, never()).process(any(), anyString(), any(), any());
    }

    @Test
    void submitUpload_givenMissingRequiredHeader_thenThrowsInvalidCsv() {
        MultipartFile bad = csvFile("missing-headers.csv", "accountId,amount\nfoo,1\n");
        when(repository.save(any(BulkUploadJob.class))).thenAnswer(inv -> {
            BulkUploadJob j = inv.getArgument(0);
            j.setJobId(UUID.randomUUID());
            return j;
        });
        assertThatThrownBy(() -> service.submitUpload(USER_ID, bad))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void getJobStatus_givenExistingJob_thenMapsAllFields() {
        UUID id = UUID.randomUUID();
        BulkUploadJob job = BulkUploadJob.builder()
                .jobId(id).userId(USER_ID).status(JobStatus.COMPLETED)
                .totalRows(10).successCount(8).errorCount(2)
                .errorDetail(List.of(ErrorRow.builder().row(3).reason("bad").build()))
                .createdAt(OffsetDateTime.now()).completedAt(OffsetDateTime.now()).build();
        when(repository.findByJobIdAndUserId(id, USER_ID)).thenReturn(Optional.of(job));

        JobStatusResponse resp = service.getJobStatus(USER_ID, id);

        assertThat(resp.getJobId()).isEqualTo(id);
        assertThat(resp.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(resp.getSuccessCount()).isEqualTo(8);
        assertThat(resp.getErrorRows()).hasSize(1);
    }

    @Test
    void getJobStatus_givenNonExistentOrNotOwned_thenThrowsJobNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByJobIdAndUserId(id, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobStatus(USER_ID, id))
                .isInstanceOf(JobNotFoundException.class);
    }

    private MockMultipartFile csvFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
