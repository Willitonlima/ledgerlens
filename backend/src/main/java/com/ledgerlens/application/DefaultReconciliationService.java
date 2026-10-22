package com.ledgerlens.application;

import com.ledgerlens.application.dto.ExecutionReceipt;
import com.ledgerlens.application.dto.ReconciliationItem;
import com.ledgerlens.application.dto.ReconciliationSummary;
import com.ledgerlens.config.ReconciliationProperties;
import com.ledgerlens.config.WorkspaceContext;
import com.ledgerlens.domain.FinancialTransaction;
import com.ledgerlens.domain.ImportBatch;
import com.ledgerlens.domain.MatchStatus;
import com.ledgerlens.domain.ReconciliationResult;
import com.ledgerlens.domain.ReconciliationRun;
import com.ledgerlens.domain.SourceType;
import com.ledgerlens.domain.TransactionOrigin;
import com.ledgerlens.infrastructure.persistence.FinancialTransactionRepository;
import com.ledgerlens.infrastructure.persistence.ImportBatchRepository;
import com.ledgerlens.infrastructure.persistence.ReconciliationResultRepository;
import com.ledgerlens.infrastructure.persistence.ReconciliationRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultReconciliationService implements ReconciliationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ImportBatchRepository importBatchRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final ReconciliationMatcher matcher;

    public DefaultReconciliationService(
            ImportBatchRepository importBatchRepository,
            FinancialTransactionRepository financialTransactionRepository,
            ReconciliationResultRepository reconciliationResultRepository,
            ReconciliationRunRepository reconciliationRunRepository,
            ReconciliationProperties properties) {
        this.importBatchRepository = importBatchRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
        this.matcher = new ReconciliationMatcher(properties);
    }

    @Override
    @Transactional
    public ExecutionReceipt execute() {
        UUID workspaceId = WorkspaceContext.requireWorkspaceId();
        ImportBatch erpBatch = importBatchRepository.findFirstByWorkspaceIdAndSourceTypeOrderByCreatedAtDescIdDesc(
                        workspaceId, SourceType.ERP)
                .orElseThrow(DefaultReconciliationService::missingSourceException);
        ImportBatch bankBatch = importBatchRepository.findFirstByWorkspaceIdAndSourceTypeOrderByCreatedAtDescIdDesc(
                        workspaceId, SourceType.BANK)
                .orElseThrow(DefaultReconciliationService::missingSourceException);

        Instant executedAt = Instant.now();
        UUID runId = UUID.randomUUID();
        reconciliationRunRepository.save(new ReconciliationRun(runId, executedAt, workspaceId));

        List<FinancialTransaction> erpTransactions =
                financialTransactionRepository.findByBatchIdAndWorkspaceIdOrderByCreatedAtAscIdAsc(
                        erpBatch.getId(), workspaceId);
        List<FinancialTransaction> bankTransactions =
                financialTransactionRepository.findByBatchIdAndWorkspaceIdOrderByCreatedAtAscIdAsc(
                        bankBatch.getId(), workspaceId);
        List<ReconciliationResult> results =
                matcher.reconcile(erpTransactions, bankTransactions, runId, executedAt);
        results.forEach(result -> result.setWorkspaceId(workspaceId));
        reconciliationResultRepository.saveAll(results);
        return new ExecutionReceipt(runId, executedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationSummary getLatest(MatchStatus status, int page, int size) {
        validatePage(page, size);
        UUID workspaceId = WorkspaceContext.requireWorkspaceId();
        var latestRun = reconciliationRunRepository.findFirstByWorkspaceIdOrderByExecutedAtDescRunIdDesc(
                workspaceId);
        if (latestRun.isEmpty()) {
            return emptySummary(page, size);
        }

        UUID runId = latestRun.get().getRunId();
        ReconciliationResultRepository.ReconciliationStats stats =
                reconciliationResultRepository.summarize(runId, workspaceId);
        Page<ReconciliationResult> results = reconciliationResultRepository.findPage(
                runId, workspaceId, status, PageRequest.of(page, size));

        List<ReconciliationItem> items = results.getContent().stream()
                .map(DefaultReconciliationService::toItem)
                .toList();
        return new ReconciliationSummary(
                latestRun.get().getExecutedAt(),
                zeroIfNull(stats.getTotalErpAmount()),
                zeroIfNull(stats.getTotalBankAmount()),
                Math.toIntExact(stats.getDivergentCount()),
                Math.toIntExact(stats.getMissingCount()),
                Math.toIntExact(stats.getMatchedCount()),
                items,
                page,
                size,
                results.getTotalElements(),
                results.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public String exportLatest(MatchStatus status) {
        UUID workspaceId = WorkspaceContext.requireWorkspaceId();
        var latestRun = reconciliationRunRepository.findFirstByWorkspaceIdOrderByExecutedAtDescRunIdDesc(
                workspaceId);
        StringBuilder csv = new StringBuilder("\uFEFFdate;description;amount;status;origin;differenceAmount;notes\r\n");
        if (latestRun.isEmpty()) {
            return csv.toString();
        }

        List<ReconciliationResult> results = reconciliationResultRepository.findAllForExport(
                latestRun.get().getRunId(), workspaceId);
        results.stream()
                .filter(result -> status == null || result.getStatus() == status)
                .map(DefaultReconciliationService::toItem)
                .forEach(item -> csv.append(csvField(item.date() == null ? "" : item.date().toString())).append(';')
                        .append(csvField(protectFormula(item.description()))).append(';')
                        .append(item.amount() == null ? "" : item.amount().toPlainString()).append(';')
                        .append(csvField(item.status().name())).append(';')
                        .append(csvField(item.origin().name())).append(';')
                        .append(item.differenceAmount() == null ? "" : item.differenceAmount().toPlainString()).append(';')
                        .append(csvField(protectFormula(item.notes() == null ? "" : item.notes())))
                        .append("\r\n"));
        return csv.toString();
    }

    private static ReconciliationPrerequisiteException missingSourceException() {
        return new ReconciliationPrerequisiteException(
                "Envie o arquivo do ERP e o do banco antes de conciliar.");
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("O parâmetro page deve ser maior ou igual a zero.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("O parâmetro size deve estar entre 1 e 100.");
        }
    }

    private static ReconciliationSummary emptySummary(int page, int size) {
        return new ReconciliationSummary(
                null, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                0, 0, 0, List.of(), page, size, 0, 0);
    }

    private static BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2) : amount;
    }

    private static ReconciliationItem toItem(ReconciliationResult result) {
        FinancialTransaction erp = result.getErpTransaction();
        FinancialTransaction bank = result.getBankTransaction();
        FinancialTransaction preferred = erp != null ? erp : bank;
        TransactionOrigin origin = erp == null ? TransactionOrigin.BANK
                : bank == null ? TransactionOrigin.ERP : TransactionOrigin.BOTH;
        return new ReconciliationItem(
                result.getId(),
                preferred.getTransactionDate(),
                preferred.getDescription(),
                preferred.getAmount(),
                result.getStatus(),
                origin,
                result.getDifferenceAmount(),
                result.getNotes());
    }

    private static String protectFormula(String value) {
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private static String csvField(String value) {
        if (value.indexOf(';') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
