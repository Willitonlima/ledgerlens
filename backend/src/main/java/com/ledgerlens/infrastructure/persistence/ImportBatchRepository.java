package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    // TODO: Adicionar as consultas necessárias para a sua lógica de negócio.
}
