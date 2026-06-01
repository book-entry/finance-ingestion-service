package com.personal.finance.bulkupload.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final BulkUploadProperties properties;

    @Bean("accountServiceRestTemplate")
    public RestTemplate accountServiceRestTemplate(RestTemplateBuilder builder) {
        BulkUploadProperties.RemoteService cfg = properties.getAccountService();
        return builder
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()))
                .build();
    }

    @Bean("transactionServiceRestTemplate")
    public RestTemplate transactionServiceRestTemplate(RestTemplateBuilder builder) {
        BulkUploadProperties.RemoteService cfg = properties.getTransactionService();
        return builder
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()))
                .build();
    }
}
