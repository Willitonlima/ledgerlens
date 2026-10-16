package com.ledgerlens.domain;

/** Ciclo de vida de um lote de importação. */
public enum BatchStatus {
    RECEIVED,
    PROCESSED,
    FAILED
}
