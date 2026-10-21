package com.ledgerlens;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BatchUploadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM financial_transaction");
        jdbc.update("DELETE FROM import_batch");
    }

    @Test
    void uploadsAndPersistsTransactions() throws Exception {
        mockMvc.perform(upload(csvFile("data,descricao,valor\n2025-01-02,Pagamento,-123.45\n")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordsImported").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.alreadyImported").value(false));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT count(*) FROM financial_transaction", Integer.class)).isEqualTo(1);
    }

    @Test
    void returns422WithInvalidRecordDetailsWithoutPersistingAnything() throws Exception {
        mockMvc.perform(upload(csvFile("data,descricao,valor\n31/02/2025,,oops\n")))
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
        mockMvc.perform(upload(csvFile(csv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").exists());

        mockMvc.perform(upload(csvFile(csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyImported").value(true))
                .andExpect(jsonPath("$.message").value("Este arquivo já havia sido importado."));
    }

    @Test
    void rejectsFilesLargerThanTenMegabytes() throws Exception {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.csv", MediaType.TEXT_PLAIN_VALUE, oversized);

        mockMvc.perform(upload(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("O arquivo excede o limite de 10 MB."));
    }

    private static MockMultipartFile csvFile(String csv) {
        return new MockMultipartFile("file", "statement.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static MockHttpServletRequestBuilder upload(MockMultipartFile file) {
        return multipart("/api/v1/batches/upload")
                .file(file)
                .param("sourceType", "ERP");
    }
}
