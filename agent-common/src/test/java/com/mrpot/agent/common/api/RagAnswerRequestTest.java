package com.mrpot.agent.common.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RagAnswerRequestTest {

  @Test
  void resolveModel_withValidModel() {
    var req = new RagAnswerRequest("query", "sess1", "openai", null, null, null, "FAST", null, null);
    assertEquals("openai", req.resolveModel());
  }

  @Test
  void resolveModel_withNullModel_defaultsToDeepseek() {
    var req = new RagAnswerRequest("query", "sess1", null, null, null, null, "FAST", null, null);
    assertEquals("deepseek", req.resolveModel());
  }

  @Test
  void resolveModel_withBlankModel_defaultsToDeepseek() {
    var req = new RagAnswerRequest("query", "sess1", "  ", null, null, null, "FAST", null, null);
    assertEquals("deepseek", req.resolveModel());
  }

  @Test
  void resolveModel_withInvalidModel_defaultsToDeepseek() {
    var req = new RagAnswerRequest("query", "sess1", "invalid_model", null, null, null, "FAST", null, null);
    assertEquals("deepseek", req.resolveModel());
  }

  @Test
  void resolveMode_withValidMode() {
    var req = new RagAnswerRequest("query", "sess1", "deepseek", null, null, null, "THOROUGH", null, null);
    assertEquals("THOROUGH", req.resolveMode());
  }

  @Test
  void resolveMode_withNullMode_defaultsToFast() {
    var req = new RagAnswerRequest("query", "sess1", "deepseek", null, null, null, null, null, null);
    assertEquals("FAST", req.resolveMode());
  }

  @Test
  void resolveMode_withBlankMode_defaultsToFast() {
    var req = new RagAnswerRequest("query", "sess1", "deepseek", null, null, null, "  ", null, null);
    assertEquals("FAST", req.resolveMode());
  }

  @Test
  void resolveScopeMode_withNullScopeMode_defaultsToAuto() {
    var req = new RagAnswerRequest("query", "sess1", "deepseek", null, null, null, "FAST", null, null);
    assertEquals(ScopeMode.AUTO, req.resolveScopeMode());
  }

  @Test
  void resolveTopK_withNullOptions_defaultsToFive() {
    var req = new RagAnswerRequest("query", "sess1", "deepseek", null, null, null, "FAST", null, null);
    assertEquals(5, req.resolveTopK());
  }

  @Test
  void resolveMinScore_withNullOptions_defaultsToMinScoreDefault() {
    var req = new RagAnswerRequest("query", "sess1", "deepseek", null, null, null, "FAST", null, null);
    assertEquals(0.10, req.resolveMinScore(), 0.0001);
  }

  @Test
  void resolveFileUrls_withNullFileUrls_returnsEmptyList() {
    var req = new RagAnswerRequest("query", "sess1", "deepseek", null, null, null, "FAST", null, null);
    var urls = req.resolveFileUrls(10);
    assertTrue(urls.isEmpty());
  }

  @Test
  void resolveFileUrls_filtersAndTrimsUrls() {
    var fileUrls = new ArrayList<>(Arrays.asList(
        "  http://example.com/file1  ",
        "http://example.com/file2",
        null,
        "   "
    ));
    var req = new RagAnswerRequest("query", "sess1", "deepseek", fileUrls, null, null, "FAST", null, null);
    var urls = req.resolveFileUrls(10);
    assertEquals(2, urls.size());
    assertEquals("http://example.com/file1", urls.get(0));
    assertEquals("http://example.com/file2", urls.get(1));
  }

  @Test
  void resolveFileUrls_limitsToMaxFiles() {
    var fileUrls = List.of("url1", "url2", "url3", "url4");
    var req = new RagAnswerRequest("query", "sess1", "deepseek", fileUrls, null, null, "FAST", null, null);
    var urls = req.resolveFileUrls(2);
    assertEquals(2, urls.size());
  }

  @Test
  void resolveFileUrls_capsAtThreeRegardlessOfMaxFiles() {
    var fileUrls = List.of("url1", "url2", "url3");
    var req = new RagAnswerRequest("query", "sess1", "deepseek", fileUrls, null, null, "FAST", null, null);
    var urls = req.resolveFileUrls(100);
    assertEquals(3, urls.size());
  }

  @Test
  void resolveFileUrls_removesNullsAndDuplicates() {
    var fileUrls = new ArrayList<>(Arrays.asList("url1", null, "url1", "url2", null));
    var req = new RagAnswerRequest("query", "sess1", "deepseek", fileUrls, null, null, "FAST", null, null);
    var urls = req.resolveFileUrls(10);
    assertEquals(2, urls.size());
    assertEquals("url1", urls.get(0));
    assertEquals("url2", urls.get(1));
  }

  @Test
  void resolveSession_withSessionId_returnsResolvedSessionNotTemporary() {
    var req = new RagAnswerRequest("query", "session123", "deepseek", null, null, null, "FAST", null, null);
    var resolved = req.resolveSession();
    assertEquals("session123", resolved.id());
    assertFalse(resolved.temporary());
  }

  @Test
  void resolveSession_withNullSessionId_returnsTemporarySession() {
    var req = new RagAnswerRequest("query", null, "deepseek", null, null, null, "FAST", null, null);
    var resolved = req.resolveSession();
    assertTrue(resolved.temporary());
    assertTrue(resolved.id().startsWith("temp-"));
  }

  @Test
  void resolveSession_withBlankSessionId_returnsTemporarySession() {
    var req = new RagAnswerRequest("query", "   ", "deepseek", null, null, null, "FAST", null, null);
    var resolved = req.resolveSession();
    assertTrue(resolved.temporary());
    assertTrue(resolved.id().startsWith("temp-"));
  }
}
