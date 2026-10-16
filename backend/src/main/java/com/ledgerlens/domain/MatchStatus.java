package com.ledgerlens.domain;

/** Resultado da conciliação para um lançamento. */
public enum MatchStatus {
    /** Lançamento encontrado nos dois lados, com valores compatíveis. */
    MATCHED,
    /** Lançamento encontrado nos dois lados, porém com divergência (ex.: valor). */
    DIVERGENT,
    /** Lançamento existe em apenas um dos lados. */
    MISSING
}
