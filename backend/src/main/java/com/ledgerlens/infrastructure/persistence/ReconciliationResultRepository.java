package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.ReconciliationResult;
import com.ledgerlens.domain.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, UUID> {

    @Query("""
            SELECT COALESCE(SUM(erp.amount), 0) AS totalErpAmount,
                   COALESCE(SUM(bank.amount), 0) AS totalBankAmount,
                   COUNT(CASE WHEN r.status = com.ledgerlens.domain.MatchStatus.MATCHED THEN 1 END) AS matchedCount,
                   COUNT(CASE WHEN r.status = com.ledgerlens.domain.MatchStatus.DIVERGENT THEN 1 END) AS divergentCount,
                   COUNT(CASE WHEN r.status = com.ledgerlens.domain.MatchStatus.MISSING THEN 1 END) AS missingCount
            FROM ReconciliationResult r
            LEFT JOIN r.erpTransaction erp
            LEFT JOIN r.bankTransaction bank
            WHERE r.runId = :runId
            """)
    ReconciliationStats summarize(@Param("runId") UUID runId);

    @EntityGraph(attributePaths = {"erpTransaction", "bankTransaction"})
    @Query(value = """
            SELECT r FROM ReconciliationResult r
            LEFT JOIN r.erpTransaction erp
            LEFT JOIN r.bankTransaction bank
            WHERE r.runId = :runId
              AND (:status IS NULL OR r.status = :status)
            ORDER BY COALESCE(erp.transactionDate, bank.transactionDate), r.id
            """,
            countQuery = """
                    SELECT COUNT(r) FROM ReconciliationResult r
                    WHERE r.runId = :runId
                      AND (:status IS NULL OR r.status = :status)
                    """)
    Page<ReconciliationResult> findPage(
            @Param("runId") UUID runId,
            @Param("status") MatchStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"erpTransaction", "bankTransaction"})
    @Query("""
            SELECT r FROM ReconciliationResult r
            LEFT JOIN r.erpTransaction erp
            LEFT JOIN r.bankTransaction bank
            WHERE r.runId = :runId
            ORDER BY COALESCE(erp.transactionDate, bank.transactionDate), r.id
            """)
    List<ReconciliationResult> findAllForExport(@Param("runId") UUID runId);

    interface ReconciliationStats {
        BigDecimal getTotalErpAmount();

        BigDecimal getTotalBankAmount();

        long getMatchedCount();

        long getDivergentCount();

        long getMissingCount();
    }
}
