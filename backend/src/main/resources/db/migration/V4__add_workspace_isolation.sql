ALTER TABLE import_batch ADD COLUMN workspace_id UUID;
UPDATE import_batch SET workspace_id = gen_random_uuid();
ALTER TABLE import_batch ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE reconciliation_run ADD COLUMN workspace_id UUID;
UPDATE reconciliation_run SET workspace_id = gen_random_uuid();
ALTER TABLE reconciliation_run ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE reconciliation_result ADD COLUMN workspace_id UUID;
UPDATE reconciliation_result result
SET workspace_id = run.workspace_id
FROM reconciliation_run run
WHERE result.run_id = run.run_id;
ALTER TABLE reconciliation_result ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX idx_import_batch_workspace_created
    ON import_batch (workspace_id, created_at DESC);
CREATE INDEX idx_reconciliation_run_workspace_executed
    ON reconciliation_run (workspace_id, executed_at DESC, run_id DESC);
CREATE INDEX idx_reconciliation_result_workspace_run
    ON reconciliation_result (workspace_id, run_id);
CREATE INDEX idx_reconciliation_result_workspace_executed
    ON reconciliation_result (workspace_id, executed_at DESC);
