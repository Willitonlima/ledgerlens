package com.ledgerlens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {

    @Id
    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected ReconciliationRun() {
    }

    public ReconciliationRun(UUID runId, Instant executedAt) {
        this.runId = runId;
        this.executedAt = executedAt;
    }

    public UUID getRunId() {
        return runId;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
