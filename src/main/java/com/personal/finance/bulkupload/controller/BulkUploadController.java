package com.personal.finance.bulkupload.controller;

import com.personal.finance.bulkupload.dto.response.JobStatusResponse;
import com.personal.finance.bulkupload.dto.response.UploadAcceptedResponse;
import com.personal.finance.bulkupload.service.BulkUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(name = "Bulk Upload", description = "CSV bulk-upload of transactions — spec §3.4.")
public class BulkUploadController {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final BulkUploadService bulkUploadService;

    /** Spec §3.4 — {@code POST /v1/bulk-upload}. Returns 202. */
    @Operation(
        summary = "Submit a CSV file for bulk transaction ingestion",
        description = "Accepts a multipart CSV file containing transaction rows and enqueues an async processing job. " +
            "Returns 202 Accepted immediately with a jobId that can be polled via GET /v1/bulk-upload/{jobId}. " +
            "Max file size is 50 MB. The CSV must include a header row; each data row maps to one transaction. " +
            "Spec §3.4."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description = "File accepted and job enqueued.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = UploadAcceptedResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "No file supplied, empty file, or the CSV header row is missing / malformed.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = com.personal.finance.common.web.ApiResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid Firebase bearer token (gateway rejects before the service is reached).",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = com.personal.finance.common.web.ApiResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "413",
            description = "File exceeds the configured maximum size (default 50 MB).",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = com.personal.finance.common.web.ApiResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "415",
            description = "Content-Type is not multipart/form-data.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = com.personal.finance.common.web.ApiResponse.class)
            )
        )
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UploadAcceptedResponse upload(
        @Parameter(
            in = ParameterIn.HEADER,
            name = USER_ID_HEADER,
            description = "Authenticated user's ID, injected by the gateway. Do not set manually.",
            required = true,
            schema = @Schema(type = "string", example = "user-abc-123")
        )
        @RequestHeader(USER_ID_HEADER) String userId,
        @Parameter(
            description = "CSV file to upload. Must be text/csv or application/octet-stream. " +
                "First row must be the header row. Maximum 50 MB.",
            required = true,
            content = @Content(
                mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                schema = @Schema(type = "string", format = "binary")
            )
        )
        @RequestParam("file") MultipartFile file) {
        return bulkUploadService.submitUpload(userId, file);
    }

    /** Spec §3.4 — {@code GET /v1/bulk-upload/{jobId}}. */
    @Operation(
        summary = "Poll the status of a bulk-upload job",
        description = "Returns the current lifecycle state of an ingestion job along with row-level success/error counts. " +
            "When the job has FAILED or COMPLETED the response includes per-row error detail in errorRows. " +
            "Spec §3.4."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Job found; status and counters returned.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = JobStatusResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Missing or invalid Firebase bearer token.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = com.personal.finance.common.web.ApiResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No job found for the given jobId, or it belongs to a different user.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = com.personal.finance.common.web.ApiResponse.class)
            )
        )
    })
    @GetMapping("/{jobId}")
    public JobStatusResponse getStatus(
        @Parameter(
            in = ParameterIn.HEADER,
            name = USER_ID_HEADER,
            description = "Authenticated user's ID, injected by the gateway. Do not set manually.",
            required = true,
            schema = @Schema(type = "string", example = "user-abc-123")
        )
        @RequestHeader(USER_ID_HEADER) String userId,
        @Parameter(
            in = ParameterIn.PATH,
            description = "UUID of the bulk-upload job returned by POST /v1/bulk-upload.",
            required = true,
            schema = @Schema(type = "string", format = "uuid", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        )
        @PathVariable("jobId") UUID jobId) {
        return bulkUploadService.getJobStatus(userId, jobId);
    }
}
