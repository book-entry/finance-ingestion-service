package com.personal.finance.bulkupload.client.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.finance.bulkupload.config.BulkUploadProperties;
import com.personal.finance.common.exception.BaseException;
import com.personal.finance.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spec §3.4 step 4: {@code GET /v1/accounts/validate?ids=...} on Account
 * Service. {@code X-User-Id} is forwarded so ownership is enforced upstream.
 */
@Component
@Slf4j
public class AccountValidationClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestTemplate restTemplate;
    private final BulkUploadProperties properties;
    private final ObjectMapper objectMapper;

    public AccountValidationClient(@Qualifier("accountServiceRestTemplate") RestTemplate restTemplate,
                                   BulkUploadProperties properties,
                                   ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AccountValidationResponse validate(String userId, Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return AccountValidationResponse.builder()
                    .valid(java.util.List.of())
                    .invalid(java.util.List.of())
                    .build();
        }
        String ids = accountIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        String url = UriComponentsBuilder
                .fromUriString(properties.getAccountService().getBaseUrl() + "/v1/accounts/validate")
                .queryParam("ids", ids)
                .build(true)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(USER_ID_HEADER, userId);
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode body = resp.getBody();
            if (body == null) {
                throw new AccountValidationCallException(new IllegalStateException("Empty body"));
            }
            // Unwrap finance-common's ApiResponse envelope (data field).
            JsonNode data = body.has("data") ? body.get("data") : body;
            return objectMapper.treeToValue(data, AccountValidationResponse.class);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Account validation call failed", ex);
            throw new AccountValidationCallException(ex);
        }
    }

    static class AccountValidationCallException extends BaseException {
        AccountValidationCallException(Throwable cause) {
            super(ErrorCode.EXT_001, HttpStatus.BAD_GATEWAY, cause);
        }
    }
}
