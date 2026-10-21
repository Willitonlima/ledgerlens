package com.ledgerlens.application;

import com.ledgerlens.config.ReconciliationProperties;
import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.MatchStatus;
import com.ledgerlens.domain.ReconciliationResult;
import com.ledgerlens.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationMatcherTest {

    private final ReconciliationMatcher matcher = new ReconciliationMatcher(new ReconciliationProperties());
    private final UUID runId = UUID.randomUUID();
    private final Instant executedAt = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void matchesSameReferenceAndAmountEvenWhenDateDiffers() {
        FinancialTransaction erp = transaction(SourceType.ERP, "REF-1", "Pagamento", "100.00", "2025-01-01");
        FinancialTransaction bank = transaction(SourceType.BANK, "ref-1", "Outra descrição", "100.00", "2025-01-20");

        List<ReconciliationResult> results = reconcile(List.of(erp), List.of(bank));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(MatchStatus.MATCHED);
            assertThat(result.getErpTransaction()).isSameAs(erp);
            assertThat(result.getBankTransaction()).isSameAs(bank);
            assertThat(result.getNotes()).contains("referência");
        });
    }

    @Test
    void choosesMostSimilarDescriptionForExactDateAndAmount() {
        FinancialTransaction erp = transaction(SourceType.ERP, null, "Pagamento fornecedor alfa", "120.00", "2025-02-02");
        FinancialTransaction first = transaction(SourceType.BANK, null, "Pagamento fornecedor beta", "120.00", "2025-02-02");
        FinancialTransaction best = transaction(SourceType.BANK, null, "Pagamento fornecedor alfa", "120.00", "2025-02-02");

        List<ReconciliationResult> results = reconcile(List.of(erp), List.of(first, best));

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().getBankTransaction()).isSameAs(best);
        assertThat(results.getFirst().getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(results.getLast().getBankTransaction()).isSameAs(first);
        assertThat(results.getLast().getStatus()).isEqualTo(MatchStatus.MISSING);
    }

    @Test
    void marksCentValueDifferenceOnAnotherDateAsDivergent() {
        FinancialTransaction erp = transaction(SourceType.ERP, null, "Pagamento mensal aluguel", "100.00", "2025-03-02");
        FinancialTransaction bank = transaction(SourceType.BANK, null, "Pagamento mensal aluguel", "99.99", "2025-03-03");

        ReconciliationResult result = reconcile(List.of(erp), List.of(bank)).getFirst();

        assertThat(result.getStatus()).isEqualTo(MatchStatus.DIVERGENT);
        assertThat(result.getDifferenceAmount()).isEqualByComparingTo("0.01");
        assertThat(result.getNotes()).contains("data");
    }

    @Test
    void marksDateToleranceWithEqualAmountAsDivergent() {
        FinancialTransaction erp = transaction(SourceType.ERP, null, "Recebimento cliente beta", "400.00", "2025-04-01");
        FinancialTransaction bank = transaction(SourceType.BANK, null, "Recebimento cliente beta", "400.00", "2025-04-03");

        ReconciliationResult result = reconcile(List.of(erp), List.of(bank)).getFirst();

        assertThat(result.getStatus()).isEqualTo(MatchStatus.DIVERGENT);
        assertThat(result.getDifferenceAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getNotes()).isEqualTo("Diverge a data.");
    }

    @Test
    void matchesSameDateWithOneCentDifferenceAsMatched() {
        FinancialTransaction erp = transaction(SourceType.ERP, null, "Tarifa de servico", "12.00", "2025-05-01");
        FinancialTransaction bank = transaction(SourceType.BANK, null, "Tarifa de servico", "12.01", "2025-05-01");

        ReconciliationResult result = reconcile(List.of(erp), List.of(bank)).getFirst();

        assertThat(result.getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(result.getDifferenceAmount()).isEqualByComparingTo("-0.01");
    }

    @Test
    void preventsDuplicateUseAndChoosesFirstBankOnDescriptionTie() {
        FinancialTransaction erp = transaction(SourceType.ERP, null, "Pagamento duplicado", "25.00", "2025-06-01");
        FinancialTransaction firstBank = transaction(SourceType.BANK, null, "Pagamento duplicado", "25.00", "2025-06-01");
        FinancialTransaction secondBank = transaction(SourceType.BANK, null, "Pagamento duplicado", "25.00", "2025-06-01");

        List<ReconciliationResult> results = reconcile(List.of(erp), List.of(firstBank, secondBank));

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().getBankTransaction()).isSameAs(firstBank);
        assertThat(results.getFirst().getStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(results.getLast().getBankTransaction()).isSameAs(secondBank);
        assertThat(results.getLast().getStatus()).isEqualTo(MatchStatus.MISSING);
        assertThat(results.getLast().getNotes()).isEqualTo("Somente no banco.");
    }

    @Test
    void reportsTransactionsThatExistOnlyInErp() {
        FinancialTransaction erp = transaction(SourceType.ERP, null, "Despesa sem extrato", "40.00", "2025-07-01");

        ReconciliationResult result = reconcile(List.of(erp), List.of()).getFirst();

        assertThat(result.getStatus()).isEqualTo(MatchStatus.MISSING);
        assertThat(result.getErpTransaction()).isSameAs(erp);
        assertThat(result.getBankTransaction()).isNull();
        assertThat(result.getNotes()).isEqualTo("Somente no ERP.");
    }

    @Test
    void reportsTransactionsThatExistOnlyInBank() {
        FinancialTransaction bank = transaction(SourceType.BANK, null, "Credito sem lançamento", "60.00", "2025-08-01");

        ReconciliationResult result = reconcile(List.of(), List.of(bank)).getFirst();

        assertThat(result.getStatus()).isEqualTo(MatchStatus.MISSING);
        assertThat(result.getErpTransaction()).isNull();
        assertThat(result.getBankTransaction()).isSameAs(bank);
        assertThat(result.getNotes()).isEqualTo("Somente no banco.");
    }

    @Test
    void normalizesCaseAccentsPunctuationAndRepeatedWhitespaceForSimilarity() {
        assertThat(ReconciliationMatcher.similarity(
                "  CRÉDITO,  Cliente\tAlfa! ",
                "credito cliente alfa")).isEqualTo(1.0);
    }

    private List<ReconciliationResult> reconcile(
            List<FinancialTransaction> erp,
            List<FinancialTransaction> bank) {
        return matcher.reconcile(erp, bank, runId, executedAt);
    }

    private static FinancialTransaction transaction(
            SourceType source,
            String reference,
            String description,
            String amount,
            String date) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setSourceType(source);
        transaction.setExternalReference(reference);
        transaction.setDescription(description);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionDate(LocalDate.parse(date));
        return transaction;
    }
}
