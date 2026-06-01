package com.personal.finance.bulkupload.client.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.finance.bulkupload.config.BulkUploadProperties;
import com.personal.finance.common.exception.BaseException;
import com.personal.finance.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Spec §3.4 step 5: calls Transaction Service
 * {@code POST /v1/transactions/batch} once per chunk of up to 500 rows.
 */
@Component
@Slf4j
public class TransactionBatchClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestTemplate restTemplate;
    private final BulkUploadProperties properties;
    private final ObjectMapper objectMapper;

    public TransactionBatchClient(@Qualifier("transactionServiceRestTemplate") RestTemplate restTemplate,
                                  BulkUploadProperties properties,
                                  ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public BatchInsertResult sendBatch(String userId, BatchRequestPayload payload) {
        String url = properties.getTransactionService().getBaseUrl() + "/v1/transactions/batch";
        HttpHeaders headers = new HttpHeaders();
        headers.set(USER_ID_HEADER, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<JsonNode> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload, headers), JsonNode.class);
            JsonNode body = resp.getBody();
            if (body == null) {
                throw new TransactionBatchCallException(new IllegalStateException("Empty body"));
            }
            JsonNode data = body.has("data") ? body.get("data") : body;
            return objectMapper.treeToValue(data, BatchInsertResult.class);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Transaction batch call failed for job={}", payload.getBulkJobId(), ex);
            throw new TransactionBatchCallException(ex);
        }
    }

    static class TransactionBatchCallException extends BaseException {
        TransactionBatchCallException(Throwable cause) {
            super(ErrorCode.EXT_001, HttpStatus.BAD_GATEWAY, cause);
        }
    }
}
