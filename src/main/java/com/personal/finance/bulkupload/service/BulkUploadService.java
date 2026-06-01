package com.personal.finance.bulkupload.service;

import com.personal.finance.bulkupload.dto.response.JobStatusResponse;
import com.personal.finance.bulkupload.dto.response.UploadAcceptedResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Implements the bulk-upload flows defined in spec §3.4. */
public interface BulkUploadService {

    /**
     * Spec §3.4 POST — synchronously creates the job row, kicks off async
     * processing, returns 202 with the jobId.
     */
    UploadAcceptedResponse submitUpload(String userId, MultipartFile file);

    /** Spec §3.4 GET — polled by the client to retrieve current state. */
    JobStatusResponse getJobStatus(String userId, UUID jobId);
}
