package com.personal.finance.bulkupload.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.*} configuration — downstream service URLs + batch size. */
@Data
@ConfigurationProperties(prefix = "app")
public class BulkUploadProperties {

    private final RemoteService accountService = new RemoteService();
    private final RemoteService transactionService = new RemoteService();
    private final BulkUpload bulkUpload = new BulkUpload();

    @Data
    public static class RemoteService {
        private String baseUrl;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;
    }

    @Data
    public static class BulkUpload {
        /** Spec §3.4 step 5 — split valid rows into chunks of 500. */
        private int batchSize = 500;
    }
}
