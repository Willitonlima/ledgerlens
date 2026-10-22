package com.ledgerlens;

import com.ledgerlens.application.WorkspaceDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String ERP_CSV = "data,descricao,valor\n2025-01-02,Pagamento,123.45\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkspaceDataService workspaceDataService;

    @Test
    void rejectsMissingAndInvalidWorkspaceHeadersAndAddsSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliations/latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "O cabeçalho X-Workspace-Id deve conter um UUID válido."))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        mockMvc.perform(get("/api/v1/reconciliations/latest").header("X-Workspace-Id", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/v1/reconciliations/latest").header("X-Workspace-Id", "1-1-1-1-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keepsUploadsAndReconciliationResultsIsolatedBetweenWorkspaces() throws Exception {
        String workspaceA = UUID.randomUUID().toString();
        String workspaceB = UUID.randomUUID().toString();
        mockMvc.perform(upload(workspaceA, csvFile(ERP_CSV), "ERP")).andExpect(status().isCreated());
        mockMvc.perform(upload(workspaceA, csvFile(ERP_CSV), "BANK"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/reconciliations/execute").header("X-Workspace-Id", workspaceA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reconciliations/latest").header("X-Workspace-Id", workspaceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalErpAmount").value(123.45));
        mockMvc.perform(get("/api/v1/reconciliations/latest").header("X-Workspace-Id", workspaceB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedAt").doesNotExist())
                .andExpect(jsonPath("$.totalErpAmount").value(0));
        mockMvc.perform(post("/api/v1/reconciliations/execute").header("X-Workspace-Id", workspaceB))
                .andExpect(status().isConflict());

        mockMvc.perform(upload(workspaceB, csvFile(ERP_CSV), "ERP")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM import_batch WHERE workspace_id = CAST(? AS UUID)", Integer.class, workspaceA))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM import_batch WHERE workspace_id = CAST(? AS UUID)", Integer.class, workspaceB))
                .isEqualTo(1);
    }

    @Test
    void rateLimitsGeneralRequestsAndUploadsWithRetryAfter() throws Exception {
        String requestWorkspace = UUID.randomUUID().toString();
        MockHttpServletRequestBuilder latest = get("/api/v1/reconciliations/latest")
                .header("X-Workspace-Id", requestWorkspace);
        for (int request = 0; request < 60; request++) {
            mockMvc.perform(latest).andExpect(status().isOk());
        }
        mockMvc.perform(latest)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        String uploadWorkspace = UUID.randomUUID().toString();
        for (int request = 0; request < 10; request++) {
            mockMvc.perform(upload(uploadWorkspace, csvFile(ERP_CSV), "ERP"))
                    .andExpect(status().is2xxSuccessful());
        }
        mockMvc.perform(upload(uploadWorkspace, csvFile(ERP_CSV), "ERP"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void rejectsAnUploadWithAnUnsupportedFileType() throws Exception {
        String workspaceId = UUID.randomUUID().toString();
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "statement.csv", MediaType.APPLICATION_PDF_VALUE, "not a CSV".getBytes());
        mockMvc.perform(upload(workspaceId, pdf, "ERP"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value(
                        "O tipo de conteúdo do arquivo não é compatível com CSV."));
    }

    @Test
    void deletesOnlyTheCurrentWorkspaceData() throws Exception {
        String workspaceA = UUID.randomUUID().toString();
        String workspaceB = UUID.randomUUID().toString();
        mockMvc.perform(upload(workspaceA, csvFile(ERP_CSV), "ERP")).andExpect(status().isCreated());
        mockMvc.perform(upload(workspaceB, csvFile(ERP_CSV), "ERP")).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/workspace").header("X-Workspace-Id", workspaceA))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM import_batch WHERE workspace_id = CAST(? AS UUID)", Integer.class, workspaceA))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM import_batch WHERE workspace_id = CAST(? AS UUID)", Integer.class, workspaceB))
                .isEqualTo(1);
    }

    @Test
    void scheduledRetentionCleanupRemovesExpiredWorkspaceData() throws Exception {
        String workspaceId = UUID.randomUUID().toString();
        mockMvc.perform(upload(workspaceId, csvFile(ERP_CSV), "ERP")).andExpect(status().isCreated());
        mockMvc.perform(upload(workspaceId, csvFile(ERP_CSV), "BANK")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/reconciliations/execute").header("X-Workspace-Id", workspaceId))
                .andExpect(status().isOk());

        Instant expired = Instant.now().minusSeconds(25 * 60 * 60);
        jdbc.update("UPDATE import_batch SET created_at = ? WHERE workspace_id = CAST(? AS UUID)",
                expired, workspaceId);
        jdbc.update("UPDATE reconciliation_run SET executed_at = ? WHERE workspace_id = CAST(? AS UUID)",
                expired, workspaceId);
        jdbc.update("UPDATE reconciliation_result SET executed_at = ? WHERE workspace_id = CAST(? AS UUID)",
                expired, workspaceId);

        workspaceDataService.deleteExpiredData();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM import_batch WHERE workspace_id = CAST(? AS UUID)", Integer.class, workspaceId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reconciliation_run WHERE workspace_id = CAST(? AS UUID)",
                Integer.class, workspaceId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reconciliation_result WHERE workspace_id = CAST(? AS UUID)",
                Integer.class, workspaceId)).isZero();
    }

    private static MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile(
                "file", "statement.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder upload(
            String workspaceId, MockMultipartFile file, String sourceType) {
        return multipart("/api/v1/batches/upload")
                .file(file)
                .param("sourceType", sourceType)
                .header("X-Workspace-Id", workspaceId);
    }
}
