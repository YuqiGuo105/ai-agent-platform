package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.FileUnderstanding;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.config.AlibabaConfig;
import com.mrpot.agent.tools.service.AttachmentService;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MCP tool for file understanding.
 */
@Component
@RequiredArgsConstructor
public class FileTools implements ToolHandler {

  private static final ObjectMapper mapper = new ObjectMapper();

  private final AttachmentService attachmentService;
  private final AlibabaConfig alibabaConfig;

  @Override
  public String name() {
    return "file.understandUrl";
  }

  @Override
  public ToolDefinition definition() {
    ObjectNode inputSchema = mapper.createObjectNode();
    inputSchema.put("type", "object");

    ObjectNode properties = mapper.createObjectNode();
    ObjectNode urlProp = mapper.createObjectNode();
    urlProp.put("type", "string");
    urlProp.put("description", "URL of the file to understand");
    properties.set("url", urlProp);

    ObjectNode maxPdfPagesProp = mapper.createObjectNode();
    maxPdfPagesProp.put("type", "integer");
    maxPdfPagesProp.put("description", "Maximum PDF pages to process (optional)");
    properties.set("maxPdfPages", maxPdfPagesProp);

    ObjectNode pdfDpiProp = mapper.createObjectNode();
    pdfDpiProp.put("type", "integer");
    pdfDpiProp.put("description", "PDF DPI for rendering (optional)");
    properties.set("pdfDpi", pdfDpiProp);

    ObjectNode maxTextCharsProp = mapper.createObjectNode();
    maxTextCharsProp.put("type", "integer");
    maxTextCharsProp.put("description", "Maximum text characters to process (optional)");
    properties.set("maxTextChars", maxTextCharsProp);

    inputSchema.set("properties", properties);
    inputSchema.set("required", mapper.createArrayNode().add("url"));

    ObjectNode outputSchema = mapper.createObjectNode();
    outputSchema.put("type", "object");
    ObjectNode outputProps = mapper.createObjectNode();

    ObjectNode textProp = mapper.createObjectNode();
    textProp.put("type", "string");
    outputProps.set("text", textProp);

    ObjectNode keywordsProp = mapper.createObjectNode();
    keywordsProp.put("type", "array");
    keywordsProp.set("items", mapper.createObjectNode().put("type", "string"));
    outputProps.set("keywords", keywordsProp);

    ObjectNode queriesProp = mapper.createObjectNode();
    queriesProp.put("type", "array");
    queriesProp.set("items", mapper.createObjectNode().put("type", "string"));
    outputProps.set("queries", queriesProp);

    ObjectNode errorProp = mapper.createObjectNode();
    errorProp.put("type", "string");
    outputProps.set("error", errorProp);

    outputSchema.set("properties", outputProps);

    return new ToolDefinition(
        name(),
        "Understand file content from URL using vision model (Qwen VL or OpenAI Vision)",
        "1.0.0",
        inputSchema,
        outputSchema,
        null,
        300L
    );
  }

  @Override
  public CallToolResponse handle(JsonNode args, ToolContext ctx) {
    String url = args.path("url").asText();
    if (url == null || url.isBlank()) {
      return new CallToolResponse(
          false,
          name(),
          null,
          false,
          null,
          Instant.now(),
          new ToolError(
              ToolError.BAD_ARGS,
              "url is required",
              false
          )
      );
    }

    try {
      FileUnderstanding understanding = understandUrl(url);

      ObjectNode result = mapper.createObjectNode();
      result.put("text", understanding.text());
      result.set("keywords", mapper.valueToTree(understanding.keywords()));
      result.set("queries", mapper.valueToTree(understanding.queries()));
      if (understanding.error() != null) {
        result.put("error", understanding.error());
      }

      return new CallToolResponse(
          understanding.isSuccess(),
          name(),
          result,
          false,
          300L,
          Instant.now(),
          null
      );
    } catch (Exception e) {
      ObjectNode result = mapper.createObjectNode();
      result.put("error", e.getMessage());

      return new CallToolResponse(
          false,
          name(),
          result,
          false,
          null,
          Instant.now(),
          new ToolError(
              ToolError.INTERNAL,
              e.getMessage(),
              false
          )
      );
    }
  }

  /**
   * Tool for understanding remote files (images, PDFs, text documents).
   * Extracts key text, keywords, and search queries from the file content.
   * 
   * Uses vision model configured via VISION_PROVIDER environment variable.
   * Defaults to Qwen VL (qwen-vl) for backward compatibility.
   * Can be switched to OpenAI Vision (openai) via configuration.
   *
   * @param url URL of the remote file to understand
   * @return FileUnderstanding containing extracted text, keywords, queries, and error status
   */
  public FileUnderstanding understandUrl(String url) {
    if (url == null || url.isBlank()) {
      return new FileUnderstanding("", List.of(), List.of(), "empty_url");
    }

    try {
      // Check which vision provider to use (defaults to Qwen VL)
      String visionProvider = alibabaConfig.getFile().getVisionProvider();
      if (visionProvider == null || visionProvider.isBlank()) {
        visionProvider = "qwen-vl"; // Default to Qwen VL
      }
      
      FileUnderstanding result;
      if ("openai".equalsIgnoreCase(visionProvider)) {
        result = attachmentService
            .understandFileUrlWithOpenAi(url)
            .timeout(Duration.ofSeconds(120))
            .block();
      } else {
        // Default to Qwen VL (backward compatible)
        result = attachmentService
            .understandFileUrlWithQwenVl(url)
            .timeout(Duration.ofSeconds(120))
            .block();
      }

      if (result == null) {
        return new FileUnderstanding("", List.of(), List.of(), "null_result");
      }

      String text = safe(result.text());
      List<String> keywords = safeList(result.keywords());
      List<String> queries = safeList(result.queries());
      String error = safe(result.error());

      return new FileUnderstanding(text, keywords, queries, error);
    } catch (Exception ex) {
      return new FileUnderstanding(
          "",
          List.of(),
          List.of(),
          "extract_failed: " + ex.getClass().getSimpleName() + ": " + safeMsg(ex)
      );
    }
  }

  /**
   * Safely get string value, return empty string if null or blank.
   */
  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }

  /**
   * Safely get list, filtering out null/blank items and trimming.
   */
  private static List<String> safeList(List<String> list) {
    if (list == null) {
      return List.of();
    }
    return list.stream()
        .filter(item -> item != null && !item.isBlank())
        .map(String::trim)
        .toList();
  }

  /**
   * Safely extract error message, limit length to prevent huge error strings.
   */
  private static String safeMsg(Throwable ex) {
    String m = ex == null ? "" : String.valueOf(ex.getMessage());
    m = m.replaceAll("\\s+", " ").trim();
    if (m.length() > 400) {
      m = m.substring(0, 400) + "...";
    }
    return m;
  }
}
