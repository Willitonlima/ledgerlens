package com.ledgerlens.application.dto;

import com.ledgerlens.domain.MatchStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Uma linha da tabela de resultados exibida no frontend. */
public record ReconciliationItem(
        UUID id,
        LocalDate date,
        String description,
        BigDecimal amount,
        MatchStatus status
) {
}
