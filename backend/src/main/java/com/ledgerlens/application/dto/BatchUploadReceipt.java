package com.ledgerlens.application.dto;

import com.ledgerlens.domain.BatchStatus;
import com.ledgerlens.domain.SourceType;

import java.util.UUID;

/** Recibo devolvido após o recebimento de um CSV. */
public record BatchUploadReceipt(
        UUID batchId,
        SourceType sourceType,
        String fileName,
        BatchStatus status,
        int recordsImported,
        /** true enquanto a resposta for simulada (remova quando implementar a lógica real). */
        boolean simulated
) {
}
