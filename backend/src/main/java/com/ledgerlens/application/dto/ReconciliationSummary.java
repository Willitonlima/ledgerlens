package com.ledgerlens.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Resultado consolidado da última conciliação (alimenta os cards e a tabela). */
public record ReconciliationSummary(
        Instant executedAt,
        BigDecimal totalErpAmount,
        BigDecimal totalBankAmount,
        int divergentCount,
        int missingCount,
        int matchedCount,
        List<ReconciliationItem> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
