package com.personal.finance.bulkupload.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Internal type — one parsed CSV row. Lives between {@code CsvParser} and
 * {@code JobProcessor}. Not exposed via any API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvTransactionRow {
    /** 1-indexed data row (excludes the CSV header). */
    private int rowIndex;
    private UUID accountId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private LocalDate transactionDate;
    private String reference;
    private String description;
    private String categoryName;
}
