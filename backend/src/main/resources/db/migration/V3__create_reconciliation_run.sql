CREATE TABLE reconciliation_run (
    run_id     UUID        PRIMARY KEY,
    executed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_reconciliation_run_executed_at
    ON reconciliation_run (executed_at DESC, run_id DESC);

ALTER TABLE reconciliation_result
    ADD CONSTRAINT fk_reconciliation_result_run
    FOREIGN KEY (run_id) REFERENCES reconciliation_run (run_id) ON DELETE CASCADE;
