package com.personal.finance.bulkupload.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personal.finance.bulkupload.dto.ErrorRow;
import com.personal.finance.bulkupload.enums.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response for {@code GET /v1/bulk-upload/{jobId}} — spec §3.4. */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Current state of a bulk-upload ingestion job.")
public class JobStatusResponse {

    @Schema(
        description = "Unique identifier of the ingestion job.",
        example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    UUID jobId;

    @Schema(
        description = "Lifecycle state of the job: PENDING, PROCESSING, COMPLETED, or FAILED.",
        example = "COMPLETED",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    JobStatus status;

    @Schema(
        description = "Total number of data rows found in the uploaded CSV (excludes the header row). " +
            "Null while the job is still PENDING.",
        example = "120"
    )
    Integer totalRows;

    @Schema(
        description = "Number of rows successfully inserted as transactions. Null while PENDING.",
        example = "115"
    )
    Integer successCount;

    @Schema(
        description = "Number of rows that failed validation or processing. Null while PENDING.",
        example = "5"
    )
    Integer errorCount;

    @Schema(
        description = "Per-row error details. Present only when errorCount > 0 and the job is COMPLETED or FAILED."
    )
    List<ErrorRow> errorRows;

    @Schema(
        description = "ISO-8601 timestamp when the job was created.",
        example = "2026-06-04T10:15:30+00:00",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    OffsetDateTime createdAt;

    @Schema(
        description = "ISO-8601 timestamp when the job reached COMPLETED or FAILED state. " +
            "Null while PENDING or PROCESSING.",
        example = "2026-06-04T10:16:05+00:00"
    )
    OffsetDateTime completedAt;
}
