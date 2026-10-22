package com.ledgerlens.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado da conciliação de um par (ou de um lançamento isolado).
 * Todas as linhas de uma mesma execução compartilham o mesmo {@code runId}.
 */
@Entity
@Table(name = "reconciliation_result")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt = Instant.now();

    /** Pode ser nulo quando o lançamento só existe no banco. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erp_transaction_id")
    private FinancialTransaction erpTransaction;

    /** Pode ser nulo quando o lançamento só existe no ERP. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_transaction_id")
    private FinancialTransaction bankTransaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status;

    @Column(name = "difference_amount", precision = 19, scale = 2)
    private BigDecimal differenceAmount;

    @Column(length = 500)
    private String notes;
}
