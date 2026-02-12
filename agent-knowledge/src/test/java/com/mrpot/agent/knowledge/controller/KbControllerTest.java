package com.mrpot.agent.knowledge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.knowledge.model.FuzzySearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {"/cleanup-h2.sql", "/data-h2.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── GET /kb/documents ──────────────────────────────────────────

    @Test
    void getAllDocuments_returnsPagedResults() throws Exception {
        mockMvc.perform(get("/kb/documents")
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void getAllDocuments_defaultPagination() throws Exception {
        mockMvc.perform(get("/kb/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    // ─── GET /kb/documents/{id} ─────────────────────────────────────

    @Test
    void getDocument_existingId_returnsDocument() throws Exception {
        mockMvc.perform(get("/kb/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void getDocument_nonExistingId_returns404() throws Exception {
        mockMvc.perform(get("/kb/documents/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDocument_invalidId_returns404() throws Exception {
        mockMvc.perform(get("/kb/documents/abc"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /kb/documents/{id} ──────────────────────────────────

    @Test
    void deleteDocument_existingId_returnsSuccess() throws Exception {
        mockMvc.perform(delete("/kb/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void deleteDocument_nonExistingId_returns404() throws Exception {
        mockMvc.perform(delete("/kb/documents/99999"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /kb/search/fuzzy ──────────────────────────────────────

    @Test
    void fuzzySearch_byKeyword_findsResults() throws Exception {
        FuzzySearchRequest request = new FuzzySearchRequest("machine", null, 0, 10);
        mockMvc.perform(post("/kb/search/fuzzy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void fuzzySearch_byKeywordAndDocType_filtersCorrectly() throws Exception {
        FuzzySearchRequest request = new FuzzySearchRequest("Spring", "GUIDE", 0, 10);
        mockMvc.perform(post("/kb/search/fuzzy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].docType").value("GUIDE"));
    }

    @Test
    void fuzzySearch_noResults_returnsEmptyList() throws Exception {
        FuzzySearchRequest request = new FuzzySearchRequest("zzzzzznonexistent", null, 0, 10);
        mockMvc.perform(post("/kb/search/fuzzy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void fuzzySearch_missingKeyword_returns400() throws Exception {
        FuzzySearchRequest request = new FuzzySearchRequest(null, null, 0, 10);
        mockMvc.perform(post("/kb/search/fuzzy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fuzzySearch_emptyKeyword_returns400() throws Exception {
        FuzzySearchRequest request = new FuzzySearchRequest("   ", null, 0, 10);
        mockMvc.perform(post("/kb/search/fuzzy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
