package com.personal.finance.bulkupload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One element of {@code error_detail} JSONB / API {@code errorRows} array. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details of a single CSV row that failed validation or processing.")
public class ErrorRow {
    /** 1-indexed CSV row number (data row 1 = first row after header). */
    @Schema(
        description = "1-indexed CSV data row number (row 1 is the first row after the header).",
        example = "3",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private int row;

    @Schema(
        description = "Human-readable explanation of why the row was rejected.",
        example = "amount must be positive"
    )
    private String reason;
}
