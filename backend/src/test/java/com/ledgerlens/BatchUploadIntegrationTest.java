package com.ledgerlens;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BatchUploadIntegrationTest extends AbstractIntegrationTest {

    private static final String WORKSPACE_ID = "5dc1d346-075c-4b84-aadb-e122c01a572d";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM reconciliation_result");
        jdbc.update("DELETE FROM reconciliation_run");
        jdbc.update("DELETE FROM financial_transaction");
        jdbc.update("DELETE FROM import_batch");
    }

    @Test
    void uploadsAndPersistsTransactions() throws Exception {
        mockMvc.perform(upload(csvFile("data,descricao,valor\n2025-01-02,Pagamento,-123.45\n"), "ERP"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordsImported").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.alreadyImported").value(false));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT count(*) FROM financial_transaction", Integer.class)).isEqualTo(1);
    }

    @Test
    void returns422WithInvalidRecordDetailsWithoutPersistingAnything() throws Exception {
        mockMvc.perform(upload(csvFile("data,descricao,valor\n31/02/2025,,oops\n"), "ERP"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].line").value(2))
                .andExpect(jsonPath("$.errors[0].field").exists())
                .andExpect(jsonPath("$.totalErrors").value(3));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT count(*) FROM import_batch", Integer.class)).isZero();
    }

    @Test
    void repeatedSameFileAndSourceReturnsExistingBatch() throws Exception {
        String csv = "data,descricao,valor\n2025-01-02,Pagamento,-123.45\n";
        mockMvc.perform(upload(csvFile(csv), "ERP"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").exists());

        mockMvc.perform(upload(csvFile(csv), "ERP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyImported").value(true))
                .andExpect(jsonPath("$.message").value("Este arquivo já havia sido importado."));
    }

    @Test
    void rejectsFilesLargerThanTenMegabytes() throws Exception {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.csv", MediaType.TEXT_PLAIN_VALUE, oversized);

        mockMvc.perform(upload(file, "ERP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O arquivo excede o limite de 10 MB."));
    }

    @Test
    void uploadsExecutesAndReturnsPagedLatestResultsAndSafeCsvExport() throws Exception {
        String erpCsv = "data,descricao,valor\n"
                + "2025-01-01,\"=alert(1)\",10.00\n"
                + "2025-01-02,Somente ERP,20.00\n";
        String bankCsv = "data,descricao,valor\n"
                + "2025-01-01,\"=alert(1)\",10.00\n"
                + "2025-01-03,Somente banco,30.00\n";

        mockMvc.perform(upload(csvFile(erpCsv), "ERP")).andExpect(status().isCreated());
        mockMvc.perform(upload(csvFile(bankCsv), "BANK")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/reconciliations/execute").header("X-Workspace-Id", WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").exists());

        mockMvc.perform(get("/api/v1/reconciliations/latest")
                        .header("X-Workspace-Id", WORKSPACE_ID).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalErpAmount").value(30.00))
                .andExpect(jsonPath("$.totalBankAmount").value(40.00))
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.missingCount").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].origin").exists());

        mockMvc.perform(get("/api/v1/reconciliations/latest")
                        .header("X-Workspace-Id", WORKSPACE_ID)
                        .param("status", "MISSING")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));

        mockMvc.perform(get("/api/v1/reconciliations/latest/export")
                        .header("X-Workspace-Id", WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("text/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("\uFEFFdate;description;amount")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString(";'=alert(1);")));
    }

    @Test
    void requiresBothSourceBatchesBeforeExecuting() throws Exception {
        mockMvc.perform(post("/api/v1/reconciliations/execute").header("X-Workspace-Id", WORKSPACE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Envie o arquivo do ERP e o do banco antes de conciliar."));
    }

    @Test
    void returnsEmptyPageBeforeAnyReconciliationRunAndRejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliations/latest").header("X-Workspace-Id", WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedAt").doesNotExist())
                .andExpect(jsonPath("$.totalErpAmount").value(0))
                .andExpect(jsonPath("$.totalBankAmount").value(0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/v1/reconciliations/latest")
                        .header("X-Workspace-Id", WORKSPACE_ID).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O parâmetro size deve estar entre 1 e 100."));
    }

    private static MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile("file", "statement.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static MockMultipartHttpServletRequestBuilder upload(MockMultipartFile file, String sourceType) {
        return multipart("/api/v1/batches/upload")
                .file(file)
                .param("sourceType", sourceType)
                .header("X-Workspace-Id", WORKSPACE_ID);
    }
}
