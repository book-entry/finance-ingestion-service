package com.personal.finance.bulkupload.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/** Response for {@code POST /v1/bulk-upload} — spec §3.4 returns 202 immediately. */
@Value
@Builder
public class UploadAcceptedResponse {
    UUID jobId;
}
