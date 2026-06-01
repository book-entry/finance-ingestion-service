package com.personal.finance.bulkupload;

import com.personal.finance.bulkupload.config.BulkUploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Bulk Upload Service entry point — spec §3.4. Owns {@code bulk_db}; accepts
 * CSV uploads, validates account ownership via Account Service, and pushes
 * rows to Transaction Service in chunks of 500.
 *
 * <p>{@link EnableAsync} powers the async pipeline: the upload endpoint
 * returns 202 immediately, processing runs on a separate thread.
 */
@SpringBootApplication
@EnableConfigurationProperties(BulkUploadProperties.class)
@EnableAsync
public class FinanceBulkUploadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceBulkUploadServiceApplication.class, args);
    }
}
