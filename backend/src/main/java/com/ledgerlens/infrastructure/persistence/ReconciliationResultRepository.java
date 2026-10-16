package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, UUID> {
    // TODO: Adicionar as consultas necessárias para a sua lógica de negócio.
}
