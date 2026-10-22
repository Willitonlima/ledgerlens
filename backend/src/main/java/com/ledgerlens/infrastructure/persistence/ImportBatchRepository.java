package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.ImportBatch;
import com.ledgerlens.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    Optional<ImportBatch> findByWorkspaceIdAndSourceTypeAndFileHash(
            UUID workspaceId, SourceType sourceType, String fileHash);

    Optional<ImportBatch> findFirstByWorkspaceIdAndSourceTypeOrderByCreatedAtDescIdDesc(
            UUID workspaceId, SourceType sourceType);

    @Modifying
    @Query("DELETE FROM ImportBatch batch WHERE batch.workspaceId = :workspaceId")
    int deleteAllByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Modifying
    @Query("DELETE FROM ImportBatch batch WHERE batch.createdAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
