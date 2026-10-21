package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {
    List<FinancialTransaction> findByBatchIdOrderByCreatedAtAscIdAsc(UUID batchId);
}
