package com.personal.finance.bulkupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One element of {@code error_detail} JSONB / API {@code errorRows} array. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorRow {
    /** 1-indexed CSV row number (data row 1 = first row after header). */
    private int row;
    private String reason;
}
