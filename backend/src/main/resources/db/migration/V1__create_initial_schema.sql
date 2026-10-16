-- Schema inicial do LedgerLens (criação básica das tabelas).

CREATE TABLE import_batch (
    id            UUID         PRIMARY KEY,
    source_type   VARCHAR(10)  NOT NULL CHECK (source_type IN ('ERP', 'BANK')),
    file_name     VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    total_records INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE financial_transaction (
    id                 UUID           PRIMARY KEY,
    batch_id           UUID           NOT NULL REFERENCES import_batch (id) ON DELETE CASCADE,
    source_type        VARCHAR(10)    NOT NULL CHECK (source_type IN ('ERP', 'BANK')),
    transaction_date   DATE           NOT NULL,
    description        VARCHAR(500)   NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL,
    external_reference VARCHAR(100),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_financial_transaction_batch ON financial_transaction (batch_id);
CREATE INDEX idx_financial_transaction_date_amount ON financial_transaction (transaction_date, amount);

CREATE TABLE reconciliation_result (
    id                  UUID           PRIMARY KEY,
    run_id              UUID           NOT NULL,
    executed_at         TIMESTAMPTZ    NOT NULL,
    erp_transaction_id  UUID           REFERENCES financial_transaction (id),
    bank_transaction_id UUID           REFERENCES financial_transaction (id),
    status              VARCHAR(20)    NOT NULL CHECK (status IN ('MATCHED', 'DIVERGENT', 'MISSING')),
    difference_amount   NUMERIC(19, 2),
    notes               VARCHAR(500)
);

CREATE INDEX idx_reconciliation_result_run ON reconciliation_result (run_id);
CREATE INDEX idx_reconciliation_result_executed_at ON reconciliation_result (executed_at DESC);
