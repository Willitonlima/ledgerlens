package com.ledgerlens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que o contexto sobe e que a migration V1 foi aplicada. */
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
    }
}
