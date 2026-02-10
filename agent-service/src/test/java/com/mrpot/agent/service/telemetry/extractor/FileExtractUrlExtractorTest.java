package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileExtractUrlExtractorTest {

    private FileExtractUrlExtractor extractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        extractor = new FileExtractUrlExtractor();
        objectMapper = new ObjectMapper();
    }

    @Test
    void supports_returns_true_for_file_extract_url() {
        assertTrue(extractor.supports("file.extract_url"));
        assertTrue(extractor.supports("FILE.EXTRACT_URL"));
        assertTrue(extractor.supports("file.extract"));
    }

    @Test
    void supports_returns_false_for_other_tools() {
        assertFalse(extractor.supports("kb.search"));
        assertFalse(extractor.supports("redis.get"));
        assertFalse(extractor.supports("other.tool"));
    }

    @Test
    void extractFromArgs_extracts_url_info() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "url": "https://example.com/docs/report.pdf",
                "format": "markdown"
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("file.extract_url", args);

        assertEquals("example.com", info.get("domain"));
        assertEquals("https", info.get("scheme"));
        assertEquals("pdf", info.get("fileExtension"));
        assertNotNull(info.get("urlLength"));
        assertEquals("markdown", info.get("requestedFormat"));
    }

    @Test
    void extractFromArgs_extracts_multiple_urls() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "urls": [
                    "https://example.com/file1.pdf",
                    "https://example.com/file2.pdf",
                    "https://example.com/file3.pdf"
                ]
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("file.extract_url", args);

        assertEquals(3, info.get("urlCount"));
    }

    @Test
    void extractFromArgs_handles_invalid_url() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "url": "not a valid url"
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("file.extract_url", args);

        assertFalse((Boolean) info.get("urlValid"));
    }

    @Test
    void extractFromArgs_handles_url_without_extension() throws Exception {
        JsonNode args = objectMapper.readTree("""
            {
                "url": "https://example.com/api/documents"
            }
            """);

        Map<String, Object> info = extractor.extractFromArgs("file.extract_url", args);

        assertEquals("example.com", info.get("domain"));
        assertNull(info.get("fileExtension"));
    }

    @Test
    void extractFromArgs_handles_null() {
        Map<String, Object> info = extractor.extractFromArgs("file.extract_url", null);
        assertTrue(info.isEmpty());
    }

    @Test
    void extractFromResult_extracts_content_info() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "contentType": "application/pdf",
                "contentLength": 1048576,
                "textLength": 5000,
                "method": "pdf-parser"
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("file.extract_url", result);

        assertEquals("application/pdf", info.get("contentType"));
        assertEquals(1048576L, info.get("contentLength"));
        assertEquals(5000, info.get("textLength"));
        assertEquals("pdf-parser", info.get("extractionMethod"));
    }

    @Test
    void extractFromResult_handles_pages() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "pageCount": 10,
                "success": true
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("file.extract_url", result);

        assertEquals(10, info.get("pageCount"));
        assertTrue((Boolean) info.get("extractionSuccess"));
    }

    @Test
    void extractFromResult_handles_error() throws Exception {
        JsonNode result = objectMapper.readTree("""
            {
                "success": false,
                "error": "File not found"
            }
            """);

        Map<String, Object> info = extractor.extractFromResult("file.extract_url", result);

        assertFalse((Boolean) info.get("extractionSuccess"));
        assertEquals("File not found", info.get("extractionError"));
    }

    @Test
    void extractFromResult_handles_null() {
        Map<String, Object> info = extractor.extractFromResult("file.extract_url", null);
        assertTrue(info.isEmpty());
    }
}
