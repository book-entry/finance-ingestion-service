package com.personal.finance.bulkupload.client.transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Mirror of Transaction Service batch response. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInsertResult {
    private int insertedCount;
    private List<FailedRow> failedRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedRow {
        private int rowIndex;
        private String reason;
    }
}
