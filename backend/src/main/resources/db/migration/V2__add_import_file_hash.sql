ALTER TABLE import_batch
    ADD COLUMN file_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_import_batch_source_hash
    ON import_batch (source_type, file_hash)
    WHERE file_hash IS NOT NULL;
