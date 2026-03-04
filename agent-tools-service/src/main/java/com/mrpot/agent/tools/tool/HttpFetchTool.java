package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.common.tool.mcp.ToolError;
import com.mrpot.agent.tools.service.ToolContext;
import com.mrpot.agent.tools.service.ToolHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * HTTP fetch tool for retrieving web content.
 * Supports GET requests with configurable timeouts and content extraction.
 */
@Slf4j
@Component
public class HttpFetchTool implements ToolHandler {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_RESPONSE_SIZE = 100_000;  // 100KB max
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final HttpClient httpClient;

    public HttpFetchTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "http.fetch";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        // url - required
        ObjectNode urlProp = mapper.createObjectNode();
        urlProp.put("type", "string");
        urlProp.put("description", "URL to fetch");
        properties.set("url", urlProp);

        // timeout - optional
        ObjectNode timeoutProp = mapper.createObjectNode();
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "Timeout in seconds (default: 30, max: 60)");
        properties.set("timeout", timeoutProp);

        // extractText - optional
        ObjectNode extractTextProp = mapper.createObjectNode();
        extractTextProp.put("type", "boolean");
        extractTextProp.put("description", "Extract plain text from HTML (default: false)");
        properties.set("extractText", extractTextProp);

        // headers - optional
        ObjectNode headersProp = mapper.createObjectNode();
        headersProp.put("type", "object");
        headersProp.put("description", "Custom headers to include in the request");
        properties.set("headers", headersProp);

        inputSchema.set("properties", properties);
        inputSchema.set("required", mapper.createArrayNode().add("url"));

        ObjectNode outputSchema = mapper.createObjectNode();
        outputSchema.put("type", "object");
        ObjectNode outputProps = mapper.createObjectNode();

        ObjectNode statusProp = mapper.createObjectNode();
        statusProp.put("type", "integer");
        statusProp.put("description", "HTTP status code");
        outputProps.set("status", statusProp);

        ObjectNode contentProp = mapper.createObjectNode();
        contentProp.put("type", "string");
        contentProp.put("description", "Response content (may be truncated)");
        outputProps.set("content", contentProp);

        ObjectNode contentTypeProp = mapper.createObjectNode();
        contentTypeProp.put("type", "string");
        contentTypeProp.put("description", "Content-Type header value");
        outputProps.set("contentType", contentTypeProp);

        outputSchema.set("properties", outputProps);

        return new ToolDefinition(
                name(),
                "Fetch content from a URL via HTTP GET request",
                "1.0.0",
                inputSchema,
                outputSchema,
                null,
                300L  // Cache for 5 minutes
        );
    }

    @Override
    public CallToolResponse handle(JsonNode args, ToolContext ctx) {
        String url = args.path("url").asText("");
        
        if (url.isEmpty()) {
            return errorResponse("url is required");
        }

        // Validate URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return errorResponse("URL must start with http:// or https://");
        }

        try {
            int timeout = Math.min(args.path("timeout").asInt(DEFAULT_TIMEOUT_SECONDS), 60);
            boolean extractText = args.path("extractText").asBoolean(false);

            // Build request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .GET()
                    .header("User-Agent", "MrPot-Agent/1.0");

            // Add custom headers if provided
            JsonNode headers = args.path("headers");
            if (headers.isObject()) {
                headers.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isTextual()) {
                        requestBuilder.header(entry.getKey(), entry.getValue().asText());
                    }
                });
            }

            HttpRequest request = requestBuilder.build();
            
            log.debug("Fetching URL: {}", url);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String content = response.body();
            boolean truncated = false;

            // Truncate if too large
            if (content != null && content.length() > MAX_RESPONSE_SIZE) {
                content = content.substring(0, MAX_RESPONSE_SIZE);
                truncated = true;
            }

            // Extract plain text from HTML if requested
            if (extractText && content != null) {
                content = extractPlainText(content);
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("unknown");

            ObjectNode result = mapper.createObjectNode();
            result.put("status", response.statusCode());
            result.put("statusOk", response.statusCode() >= 200 && response.statusCode() < 300);
            result.put("contentType", contentType);
            result.put("contentLength", content != null ? content.length() : 0);
            result.put("truncated", truncated);
            result.put("content", content);
            result.put("url", url);

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            
            return new CallToolResponse(
                    success,
                    name(),
                    result,
                    success,
                    success ? 300L : null,
                    Instant.now(),
                    success ? null : new ToolError(
                            "HTTP_" + response.statusCode(),
                            "HTTP request failed with status " + response.statusCode(),
                            response.statusCode() >= 500
                    )
            );
        } catch (java.net.MalformedURLException e) {
            return errorResponse("Invalid URL format: " + e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            return new CallToolResponse(
                    false,
                    name(),
                    null,
                    false,
                    null,
                    Instant.now(),
                    new ToolError(ToolError.TIMEOUT, "Request timed out", true)
            );
        } catch (java.io.IOException e) {
            return new CallToolResponse(
                    false,
                    name(),
                    null,
                    false,
                    null,
                    Instant.now(),
                    new ToolError(ToolError.INTERNAL, "Network error: " + e.getMessage(), true)
            );
        } catch (Exception e) {
            log.error("HTTP fetch error for {}: {}", url, e.getMessage(), e);
            return errorResponse("Fetch error: " + e.getMessage());
        }
    }

    /**
     * Extract plain text from HTML content by removing tags.
     * This is a simple extraction - not a full HTML parser.
     */
    private String extractPlainText(String html) {
        if (html == null) return "";
        
        // Remove script and style elements
        String text = html.replaceAll("(?i)<script[^>]*>[\\s\\S]*?</script>", " ");
        text = text.replaceAll("(?i)<style[^>]*>[\\s\\S]*?</style>", " ");
        
        // Remove HTML tags
        text = text.replaceAll("<[^>]+>", " ");
        
        // Decode common HTML entities
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&quot;", "\"");
        text = text.replaceAll("&#39;", "'");
        
        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }

    private CallToolResponse errorResponse(String message) {
        return new CallToolResponse(
                false,
                name(),
                null,
                false,
                null,
                Instant.now(),
                new ToolError(ToolError.BAD_ARGS, message, false)
        );
    }
}
