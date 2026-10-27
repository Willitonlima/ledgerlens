DROP INDEX uq_import_batch_source_hash;

CREATE UNIQUE INDEX uq_import_batch_workspace_source_hash
    ON import_batch (workspace_id, source_type, file_hash)
    WHERE file_hash IS NOT NULL;
