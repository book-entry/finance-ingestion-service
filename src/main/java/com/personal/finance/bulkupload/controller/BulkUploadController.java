package com.personal.finance.bulkupload.controller;

import com.personal.finance.bulkupload.dto.response.JobStatusResponse;
import com.personal.finance.bulkupload.dto.response.UploadAcceptedResponse;
import com.personal.finance.bulkupload.service.BulkUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** REST entry points for bulk upload — spec §3.4. Routing only. */
@RestController
@RequestMapping("/v1/bulk-upload")
@RequiredArgsConstructor
public class BulkUploadController {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final BulkUploadService bulkUploadService;

    /** Spec §3.4 — {@code POST /v1/bulk-upload}. Returns 202. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UploadAcceptedResponse upload(@RequestHeader(USER_ID_HEADER) String userId,
                                         @RequestParam("file") MultipartFile file) {
        return bulkUploadService.submitUpload(userId, file);
    }

    /** Spec §3.4 — {@code GET /v1/bulk-upload/{jobId}}. */
    @GetMapping("/{jobId}")
    public JobStatusResponse getStatus(@RequestHeader(USER_ID_HEADER) String userId,
                                       @PathVariable("jobId") UUID jobId) {
        return bulkUploadService.getJobStatus(userId, jobId);
    }
}
