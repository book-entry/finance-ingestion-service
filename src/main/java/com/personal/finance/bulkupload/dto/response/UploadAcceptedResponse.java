package com.personal.finance.bulkupload.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/** Response for {@code POST /v1/bulk-upload} — spec §3.4 returns 202 immediately. */
@Value
@Builder
@Schema(description = "Acknowledgement returned immediately when a CSV file is accepted for async processing.")
public class UploadAcceptedResponse {
    @Schema(
        description = "Unique identifier for the ingestion job. Use this to poll GET /v1/bulk-upload/{jobId}.",
        example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    UUID jobId;
}
