package com.ledgerlens.application;

import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.ImportBatch;
import com.ledgerlens.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCsvParserServiceTest {

    private final DefaultCsvParserService parser = new DefaultCsvParserService();

    @Test
    void parsesSupportedDateAndAmountFormatsAndReorderedHeaders() {
        String csv = "valor;description;date;documento\r\n1.234,56;\"Pagamento fornecedor\";31/12/2025;REF-1\r\n"
                + "1234.56;Crédito;2025-01-02;REF-2\r\n"
                + "(1.234,56);Débito;2025-02-03;REF-3\r\n"
                + "-1234,56;Débito;2025-02-04;REF-4\r\n"
                + "R$ 1234,56;Crédito;2025-02-05;REF-5\r\n";

        List<FinancialTransaction> transactions = parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(transactions).hasSize(5);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(transactions.get(0).getAmount().toPlainString()).isEqualTo("1234.56");
        assertThat(transactions.get(1).getTransactionDate()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(transactions.get(1).getAmount().toPlainString()).isEqualTo("1234.56");
        assertThat(transactions.get(2).getAmount().toPlainString()).isEqualTo("-1234.56");
        assertThat(transactions.get(3).getAmount().toPlainString()).isEqualTo("-1234.56");
        assertThat(transactions.get(4).getAmount().toPlainString()).isEqualTo("1234.56");
        assertThat(transactions.get(0).getExternalReference()).isEqualTo("REF-1");
    }

    @Test
    void parsesCommaDelimitedCsvWithQuotedSeparatorsAndUtf8Bom() {
        String csv = "\uFEFF\"Descrição\",Data,Valor,Referência\n"
                + "\"Pagamento, filial \"\"Sul\"\"\",01/03/2025,\"(R$ 1.234,56)\",DOC-7\n";

        List<FinancialTransaction> transactions = parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getDescription()).isEqualTo("Pagamento, filial \"Sul\"");
        assertThat(transactions.get(0).getAmount().toPlainString()).isEqualTo("-1234.56");
    }

    @Test
    void fallsBackToIso88591AndAllowsOptionalReference() {
        byte[] bytes = "data;histórico;amount\n2025-01-02;Café;1234.56\n"
                .getBytes(StandardCharsets.ISO_8859_1);

        List<FinancialTransaction> transactions = parse(bytes);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getDescription()).isEqualTo("Café");
        assertThat(transactions.get(0).getExternalReference()).isNull();
    }

    @Test
    void returnsLineAndFieldForInvalidRecords() {
        String csv = "data,descricao,valor\n"
                + "31/02/2025,Pagamento,abc\n"
                + "2025-01-01,,10.00\n";

        assertThatThrownBy(() -> parse(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(CsvValidationException.class, exception -> {
                    assertThat(exception.getErrors()).extracting(CsvValidationException.CsvError::line)
                            .contains(2, 3);
                    assertThat(exception.getErrors()).extracting(CsvValidationException.CsvError::field)
                            .contains("date", "amount", "description");
                    assertThat(exception.getTotalErrors()).isEqualTo(3);
                });
    }

    @Test
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> parse(new byte[0]))
                .isInstanceOf(CsvValidationException.class)
                .hasMessageContaining("cabeçalho");
    }

    @Test
    void limitsReportedErrorsButCountsAllInvalidRows() {
        StringBuilder csv = new StringBuilder("data,descricao,valor\n");
        for (int line = 0; line < 60; line++) {
            csv.append("not-a-date,,invalid\n");
        }

        assertThatThrownBy(() -> parse(csv.toString().getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(CsvValidationException.class, exception -> {
                    assertThat(exception.getErrors()).hasSize(50);
                    assertThat(exception.getTotalErrors()).isEqualTo(180);
                });
    }

    private List<FinancialTransaction> parse(byte[] content) {
        ImportBatch batch = new ImportBatch();
        batch.setSourceType(SourceType.ERP);
        return parser.parse(new ByteArrayInputStream(content), batch);
    }
}
