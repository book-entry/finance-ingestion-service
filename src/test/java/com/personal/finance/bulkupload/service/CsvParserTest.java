package com.personal.finance.bulkupload.service;

import com.personal.finance.bulkupload.dto.internal.CsvTransactionRow;
import com.personal.finance.bulkupload.exception.InvalidCsvException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    @Test
    void parse_givenValidCsv_thenReturnsAllRows_withNoErrors() {
        String csv = """
                accountId,entryType,amount,currency,transactionDate,reference,description,categoryName
                a4d4f7c8-1111-1111-1111-111111111111,DEBIT,12.50,USD,2026-05-24,INV-1,Lunch,Food
                a4d4f7c8-1111-1111-1111-111111111111,CREDIT,100.00,USD,2026-05-25,INV-2,Refund,
                """;
        CsvParser.ParseResult result = parser.parse(stream(csv));

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getErrors()).isEmpty();

        CsvTransactionRow first = result.getRows().get(0);
        assertThat(first.getRowIndex()).isEqualTo(1);
        assertThat(first.getEntryType()).isEqualTo("DEBIT");
        assertThat(first.getAmount()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(first.getTransactionDate()).isEqualTo(LocalDate.of(2026, 5, 24));
        assertThat(first.getCategoryName()).isEqualTo("Food");

        // Empty categoryName collapses to null per parser contract.
        assertThat(result.getRows().get(1).getCategoryName()).isNull();
    }

    @Test
    void parse_givenInvalidUuid_thenCollectsRowError_andStillReturnsOtherRows() {
        String csv = """
                accountId,entryType,amount,currency,transactionDate
                not-a-uuid,DEBIT,1,USD,2026-05-24
                a4d4f7c8-1111-1111-1111-111111111111,DEBIT,1,USD,2026-05-24
                """;
        CsvParser.ParseResult result = parser.parse(stream(csv));

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getReason()).contains("UUID");
    }

    @Test
    void parse_givenInvalidEntryType_thenCollectsRowError() {
        String csv = """
                accountId,entryType,amount,currency,transactionDate
                a4d4f7c8-1111-1111-1111-111111111111,TRANSFER,1,USD,2026-05-24
                """;
        CsvParser.ParseResult result = parser.parse(stream(csv));

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("DEBIT");
    }

    @Test
    void parse_givenZeroAmount_thenCollectsRowError() {
        String csv = """
                accountId,entryType,amount,currency,transactionDate
                a4d4f7c8-1111-1111-1111-111111111111,DEBIT,0,USD,2026-05-24
                """;
        CsvParser.ParseResult result = parser.parse(stream(csv));

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("greater than 0");
    }

    @Test
    void parse_givenInvalidDate_thenCollectsRowError() {
        String csv = """
                accountId,entryType,amount,currency,transactionDate
                a4d4f7c8-1111-1111-1111-111111111111,DEBIT,1,USD,24/05/2026
                """;
        CsvParser.ParseResult result = parser.parse(stream(csv));

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getReason()).contains("YYYY-MM-DD");
    }

    @Test
    void parse_givenMissingRequiredHeader_thenThrowsInvalidCsv() {
        String csv = """
                accountId,amount,currency,transactionDate
                a4d4f7c8-1111-1111-1111-111111111111,1,USD,2026-05-24
                """;
        assertThatThrownBy(() -> parser.parse(stream(csv)))
                .isInstanceOf(InvalidCsvException.class)
                .hasMessageContaining("entryType");
    }

    @Test
    void parse_givenBlankAmount_thenCollectsRowError() {
        String csv = """
                accountId,entryType,amount,currency,transactionDate
                a4d4f7c8-1111-1111-1111-111111111111,DEBIT,,USD,2026-05-24
                """;
        CsvParser.ParseResult result = parser.parse(stream(csv));

        assertThat(result.getErrors()).hasSize(1);
    }

    private ByteArrayInputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
