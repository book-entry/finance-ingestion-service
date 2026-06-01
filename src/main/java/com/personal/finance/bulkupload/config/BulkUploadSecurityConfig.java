package com.personal.finance.bulkupload.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Per-service security chain — same shape as the other Book Entry services. */
@Configuration
@Slf4j
public class BulkUploadSecurityConfig {

    private static final String[] PERMITTED_PATTERNS = {
            "/v1/bulk-upload/**",
            "/actuator/health",
            "/actuator/info"
    };

    @Bean("bulkUploadSecurityFilterChain")
    @Order(0)
    public SecurityFilterChain bulkUploadSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PERMITTED_PATTERNS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        log.info("Bulk-upload security chain registered — permitting: {}",
                String.join(", ", PERMITTED_PATTERNS));
        return http.build();
    }
}
