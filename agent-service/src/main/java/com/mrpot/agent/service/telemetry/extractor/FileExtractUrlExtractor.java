package com.mrpot.agent.service.telemetry.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Extractor for file.extract_url tool - extracts URL domain and content type info.
 */
@Component
public class FileExtractUrlExtractor implements ToolKeyInfoExtractor {
    
    private static final String TOOL_NAME = "file.extract_url";
    
    @Override
    public boolean supports(String toolName) {
        return TOOL_NAME.equalsIgnoreCase(toolName) 
            || "file.extract".equalsIgnoreCase(toolName);
    }
    
    @Override
    public Map<String, Object> extractFromArgs(String toolName, JsonNode args) {
        Map<String, Object> info = new HashMap<>();
        
        if (args == null) {
            return info;
        }
        
        // Extract URL and parse domain
        if (args.has("url")) {
            String url = args.get("url").asText();
            info.put("urlLength", url.length());
            
            try {
                URI uri = new URI(url);
                info.put("domain", uri.getHost());
                info.put("scheme", uri.getScheme());
                
                // Extract file extension if present
                String path = uri.getPath();
                if (path != null && path.contains(".")) {
                    String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
                    if (ext.length() <= 10) {
                        info.put("fileExtension", ext);
                    }
                }
            } catch (Exception ignored) {
                info.put("urlValid", false);
            }
        }
        
        // Extract URLs array if present
        if (args.has("urls") && args.get("urls").isArray()) {
            info.put("urlCount", args.get("urls").size());
        }
        
        // Extract format preference
        if (args.has("format")) {
            info.put("requestedFormat", args.get("format").asText());
        }
        
        return info;
    }
    
    @Override
    public Map<String, Object> extractFromResult(String toolName, JsonNode result) {
        Map<String, Object> info = new HashMap<>();
        
        if (result == null) {
            return info;
        }
        
        // Extract content type
        if (result.has("contentType")) {
            info.put("contentType", result.get("contentType").asText());
        }
        
        // Extract content length/size
        if (result.has("contentLength")) {
            info.put("contentLength", result.get("contentLength").asLong());
        }
        
        if (result.has("textLength")) {
            info.put("textLength", result.get("textLength").asInt());
        }
        
        // Extract extraction method used
        if (result.has("method")) {
            info.put("extractionMethod", result.get("method").asText());
        }
        
        // Extract page count for documents
        if (result.has("pageCount")) {
            info.put("pageCount", result.get("pageCount").asInt());
        }
        
        // Extract success/failure
        if (result.has("success")) {
            info.put("extractionSuccess", result.get("success").asBoolean());
        }
        
        // Extract error if present
        if (result.has("error")) {
            info.put("extractionError", truncate(result.get("error").asText(), 200));
        }
        
        return info;
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
