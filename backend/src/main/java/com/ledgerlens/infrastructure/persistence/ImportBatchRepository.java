package com.ledgerlens.infrastructure.persistence;

import com.ledgerlens.domain.ImportBatch;
import com.ledgerlens.domain.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    Optional<ImportBatch> findBySourceTypeAndFileHash(
            SourceType sourceType, String fileHash);

    Optional<ImportBatch> findFirstBySourceTypeOrderByCreatedAtDescIdDesc(SourceType sourceType);
}
