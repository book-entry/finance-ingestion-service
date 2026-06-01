package com.personal.finance.bulkupload.service;

import com.personal.finance.bulkupload.dto.ErrorRow;
import com.personal.finance.bulkupload.dto.internal.CsvTransactionRow;
import com.personal.finance.bulkupload.exception.InvalidCsvException;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parses the spec §3.4 CSV layout:
 * <pre>accountId, entryType, amount, currency, transactionDate, reference, description, categoryName</pre>
 *
 * <p>Returns parsed rows and accumulates per-row failures. The {@code rowIndex}
 * on both lists is 1-indexed and excludes the header — so row {@code 1} is the
 * first data line.
 */
@Component
@Slf4j
public class CsvParser {

    private static final String[] EXPECTED_HEADERS = {
            "accountId", "entryType", "amount", "currency",
            "transactionDate", "reference", "description", "categoryName"
    };

    /** Outcome of one parse pass — both lists may be non-empty. */
    @Value
    public static class ParseResult {
        List<CsvTransactionRow> rows;
        List<ErrorRow> errors;
    }

    public ParseResult parse(InputStream inputStream) {
        List<CsvTransactionRow> rows = new ArrayList<>();
        List<ErrorRow> errors = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             var parser = format.parse(reader)) {

            assertHeaders(parser.getHeaderNames());

            int rowIndex = 0;
            for (CSVRecord record : parser) {
                rowIndex++; // 1-indexed data row (header excluded)
                try {
                    rows.add(toRow(rowIndex, record));
                } catch (Exception ex) {
                    log.debug("CSV row {} rejected: {}", rowIndex, ex.getMessage());
                    errors.add(ErrorRow.builder()
                            .row(rowIndex)
                            .reason(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                            .build());
                }
            }
        } catch (IOException ex) {
            throw new InvalidCsvException("CSV could not be read: " + ex.getMessage(), ex);
        }
        return new ParseResult(rows, errors);
    }

    private void assertHeaders(List<String> actual) {
        for (String required : List.of("accountId", "entryType", "amount", "currency", "transactionDate")) {
            if (!actual.contains(required)) {
                throw new InvalidCsvException(
                        "CSV missing required header '" + required + "'. Expected headers: "
                                + String.join(", ", EXPECTED_HEADERS));
            }
        }
    }

    private CsvTransactionRow toRow(int rowIndex, CSVRecord record) {
        String accountIdStr = required(record, "accountId");
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("accountId is not a valid UUID");
        }

        String entryType = required(record, "entryType");
        if (!entryType.equals("DEBIT") && !entryType.equals("CREDIT")) {
            throw new IllegalArgumentException("entryType must be DEBIT or CREDIT");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(required(record, "amount"));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("amount is not a valid number");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }

        String currency = required(record, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code");
        }

        LocalDate transactionDate;
        try {
            transactionDate = LocalDate.parse(required(record, "transactionDate"));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("transactionDate must be YYYY-MM-DD");
        }

        return CsvTransactionRow.builder()
                .rowIndex(rowIndex)
                .accountId(accountId)
                .entryType(entryType)
                .amount(amount)
                .currency(currency)
                .transactionDate(transactionDate)
                .reference(optional(record, "reference"))
                .description(optional(record, "description"))
                .categoryName(optional(record, "categoryName"))
                .build();
    }

    private static String required(CSVRecord record, String name) {
        if (!record.isMapped(name)) {
            throw new IllegalArgumentException("missing field '" + name + "'");
        }
        String value = record.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("field '" + name + "' is blank");
        }
        return value;
    }

    private static String optional(CSVRecord record, String name) {
        if (!record.isMapped(name)) return null;
        String value = record.get(name);
        return value == null || value.isBlank() ? null : value;
    }
}
