package com.ledgerlens.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Recibo devolvido ao disparar uma execução de conciliação. */
public record ExecutionReceipt(
        UUID runId,
        Instant executedAt
) {
}
