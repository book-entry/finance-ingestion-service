package com.personal.finance.bulkupload.controller;

import com.personal.finance.bulkupload.dto.response.JobStatusResponse;
import com.personal.finance.bulkupload.dto.response.UploadAcceptedResponse;
import com.personal.finance.bulkupload.enums.JobStatus;
import com.personal.finance.bulkupload.exception.InvalidCsvException;
import com.personal.finance.bulkupload.exception.JobNotFoundException;
import com.personal.finance.bulkupload.service.BulkUploadService;
import com.personal.finance.common.web.ApiResponseBodyAdvice;
import com.personal.finance.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BulkUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ApiResponseBodyAdvice.class})
class BulkUploadControllerTest {

    private static final String USER_ID = "user-bulk";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Autowired MockMvc mvc;
    @MockitoBean BulkUploadService bulkUploadService;

    @Test
    void upload_givenValidCsv_thenReturns202_withJobId() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(bulkUploadService.submitUpload(eq(USER_ID), any()))
                .thenReturn(UploadAcceptedResponse.builder().jobId(jobId).build());

        MockMultipartFile file = new MockMultipartFile("file", "tx.csv", "text/csv", "header\n".getBytes());

        mvc.perform(multipart("/v1/bulk-upload").file(file).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()));
    }

    @Test
    void upload_givenMissingFile_thenReturns400() throws Exception {
        mvc.perform(multipart("/v1/bulk-upload").header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_givenMissingUserId_thenReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tx.csv", "text/csv", "header\n".getBytes());
        mvc.perform(multipart("/v1/bulk-upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_givenInvalidCsvFromService_thenReturns400_invalidCsv() throws Exception {
        when(bulkUploadService.submitUpload(eq(USER_ID), any()))
                .thenThrow(new InvalidCsvException("File must be a CSV"));
        MockMultipartFile file = new MockMultipartFile("file", "tx.pdf", "application/pdf", "x".getBytes());

        mvc.perform(multipart("/v1/bulk-upload").file(file).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CSV"));
    }

    @Test
    void getStatus_givenExistingJob_thenReturns200WithCounts() throws Exception {
        UUID id = UUID.randomUUID();
        when(bulkUploadService.getJobStatus(USER_ID, id)).thenReturn(JobStatusResponse.builder()
                .jobId(id).status(JobStatus.COMPLETED)
                .totalRows(5).successCount(4).errorCount(1)
                .createdAt(OffsetDateTime.now()).completedAt(OffsetDateTime.now())
                .build());

        mvc.perform(get("/v1/bulk-upload/{id}", id).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.successCount").value(4))
                .andExpect(jsonPath("$.data.errorCount").value(1));
    }

    @Test
    void getStatus_givenNonExistent_thenReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(bulkUploadService.getJobStatus(USER_ID, id)).thenThrow(new JobNotFoundException(id));

        mvc.perform(get("/v1/bulk-upload/{id}", id).header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void getStatus_givenMissingUserId_thenReturns400() throws Exception {
        mvc.perform(get("/v1/bulk-upload/{id}", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }
}
