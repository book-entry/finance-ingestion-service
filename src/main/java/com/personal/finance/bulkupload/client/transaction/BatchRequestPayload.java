package com.personal.finance.bulkupload.client.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request payload mirror for Transaction Service
 * {@code POST /v1/transactions/batch}. Lives here so the bulk-upload service
 * does not depend on the transaction-service module at compile time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRequestPayload {
    private UUID bulkJobId;
    private List<Row> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private UUID accountId;
        private String entryType;
        private BigDecimal amount;
        private String currency;
        private LocalDate transactionDate;
        private String reference;
        private String description;
        private UUID categoryId;
        private String categoryName;
    }
}
