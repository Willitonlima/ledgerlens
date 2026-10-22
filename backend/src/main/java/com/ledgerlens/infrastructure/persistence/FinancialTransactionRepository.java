package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {
    @Query("""
            SELECT ft FROM FinancialTransaction ft
            WHERE ft.batch.id = :batchId
              AND ft.batch.workspaceId = :workspaceId
            ORDER BY ft.createdAt ASC, ft.id ASC
            """)
    List<FinancialTransaction> findByBatchIdAndWorkspaceIdOrderByCreatedAtAscIdAsc(
            @Param("batchId") UUID batchId, @Param("workspaceId") UUID workspaceId);
}
