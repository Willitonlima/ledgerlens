package com.ledgerlens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que o contexto sobe e que as migrations são aplicadas. */
class SchemaSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayCreatesInitialTables() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('import_batch', 'financial_transaction', 'reconciliation_result')
                """, Integer.class);

        assertThat(tables).isEqualTo(3);

        Integer hashColumn = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'import_batch'
                  AND column_name = 'file_hash'
                """, Integer.class);
        assertThat(hashColumn).isEqualTo(1);

        Integer workspaceColumns = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'workspace_id'
                  AND table_name IN ('import_batch', 'reconciliation_run', 'reconciliation_result')
                """, Integer.class);
        assertThat(workspaceColumns).isEqualTo(3);
    }
}
