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

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    protected ReconciliationRun() {
    }

    public ReconciliationRun(UUID runId, Instant executedAt, UUID workspaceId) {
        this.runId = runId;
        this.executedAt = executedAt;
        this.workspaceId = workspaceId;
    }

    public UUID getRunId() {
        return runId;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }
}
