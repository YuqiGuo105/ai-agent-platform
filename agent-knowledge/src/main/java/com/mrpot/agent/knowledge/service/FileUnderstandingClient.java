package com.mrpot.agent.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.tool.FileUnderstanding;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUnderstandingClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${mcp.tools.base-url:http://localhost:8081}")
    private String toolsBaseUrl;

    public FileUnderstanding understandUrl(String fileUrl) {
        try {
            JsonNode args = objectMapper.valueToTree(Map.of("url", fileUrl));
            CallToolRequest request = new CallToolRequest(
                    "file.understandUrl",
                    args,
                    null,
                    null,
                    null,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<CallToolRequest> entity = new HttpEntity<>(request, headers);

            CallToolResponse response = restTemplate.postForObject(
                    toolsBaseUrl + "/mcp/call_tool",
                    entity,
                    CallToolResponse.class
            );

            if (response == null) {
                return new FileUnderstanding("", java.util.List.of(), java.util.List.of(), "null_response");
            }

            if (!response.ok()) {
                String errorMsg = response.error() != null ? response.error().message() : "tool_error";
                return new FileUnderstanding("", java.util.List.of(), java.util.List.of(), errorMsg);
            }

            JsonNode resultNode = response.result();
            if (resultNode == null) {
                return new FileUnderstanding("", java.util.List.of(), java.util.List.of(), "empty_result");
            }
            return objectMapper.treeToValue(resultNode, FileUnderstanding.class);
        } catch (Exception e) {
            log.error("Failed to extract text from file URL {}: {}", fileUrl, e.getMessage());
            return new FileUnderstanding("", java.util.List.of(), java.util.List.of(), e.getMessage());
        }
    }
}
