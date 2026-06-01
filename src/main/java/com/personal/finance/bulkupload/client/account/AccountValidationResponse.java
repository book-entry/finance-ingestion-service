package com.personal.finance.bulkupload.client.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Mirror of Account Service {@code /v1/accounts/validate} response payload. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountValidationResponse {
    private List<UUID> valid;
    private List<UUID> invalid;
}
