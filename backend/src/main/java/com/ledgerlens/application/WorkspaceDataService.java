package com.ledgerlens.application;

import com.ledgerlens.config.WorkspaceContext;
import com.ledgerlens.infrastructure.persistence.ImportBatchRepository;
import com.ledgerlens.infrastructure.persistence.ReconciliationResultRepository;
import com.ledgerlens.infrastructure.persistence.ReconciliationRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class WorkspaceDataService {

    private final ImportBatchRepository importBatchRepository;
    private final ReconciliationResultRepository reconciliationResultRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final long retentionHours;

    public WorkspaceDataService(
            ImportBatchRepository importBatchRepository,
            ReconciliationResultRepository reconciliationResultRepository,
            ReconciliationRunRepository reconciliationRunRepository,
            @Value("${ledgerlens.retention-hours:24}") long retentionHours) {
        if (retentionHours <= 0) {
            throw new IllegalArgumentException("ledgerlens.retention-hours deve ser maior que zero.");
        }
        this.importBatchRepository = importBatchRepository;
        this.reconciliationResultRepository = reconciliationResultRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
        this.retentionHours = retentionHours;
    }

    @Transactional
    public void deleteCurrentWorkspace() {
        UUID workspaceId = WorkspaceContext.requireWorkspaceId();
        reconciliationResultRepository.deleteAllByWorkspaceId(workspaceId);
        reconciliationRunRepository.deleteAllByWorkspaceId(workspaceId);
        importBatchRepository.deleteAllByWorkspaceId(workspaceId);
    }

    @Scheduled(fixedDelayString = "${ledgerlens.cleanup-interval-ms:3600000}")
    @Transactional
    public void deleteExpiredData() {
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        reconciliationResultRepository.deleteExpired(cutoff);
        reconciliationRunRepository.deleteExpired(cutoff);
        importBatchRepository.deleteExpired(cutoff);
    }
}
