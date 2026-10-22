package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    Optional<ReconciliationRun> findFirstByWorkspaceIdOrderByExecutedAtDescRunIdDesc(UUID workspaceId);

    @Modifying
    @Query("DELETE FROM ReconciliationRun run WHERE run.workspaceId = :workspaceId")
    int deleteAllByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Modifying
    @Query("DELETE FROM ReconciliationRun run WHERE run.executedAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
