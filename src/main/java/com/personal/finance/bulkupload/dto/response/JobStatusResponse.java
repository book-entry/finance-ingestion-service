package com.personal.finance.bulkupload.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.personal.finance.bulkupload.dto.ErrorRow;
import com.personal.finance.bulkupload.enums.JobStatus;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response for {@code GET /v1/bulk-upload/{jobId}} — spec §3.4. */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusResponse {
    UUID jobId;
    JobStatus status;
    Integer totalRows;
    Integer successCount;
    Integer errorCount;
    List<ErrorRow> errorRows;
    OffsetDateTime createdAt;
    OffsetDateTime completedAt;
}
