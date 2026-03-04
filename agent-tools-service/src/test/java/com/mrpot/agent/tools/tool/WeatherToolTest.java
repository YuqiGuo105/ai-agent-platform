package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import com.mrpot.agent.tools.service.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherToolTest {
    
    private WeatherTool tool;
    private ObjectMapper mapper;
    private ToolContext ctx;
    
    @BeforeEach
    void setUp() {
        tool = new WeatherTool();
        mapper = new ObjectMapper();
        ctx = new ToolContext("test-trace", "test-session");
    }
    
    @Test
    void name_returns_correct_name() {
        assertEquals("weather.query", tool.name());
    }
    
    @Test
    void definition_has_required_fields() {
        ToolDefinition def = tool.definition();
        
        assertNotNull(def);
        assertEquals("weather.query", def.name());
        assertEquals("1.0.0", def.version());
        assertNotNull(def.description());
        assertTrue(def.description().contains("weather"));
        assertNotNull(def.inputSchema());
        assertEquals(600L, def.ttlHintSeconds());
    }
    
    @Test
    void handle_returns_error_when_location_missing() {
        ObjectNode args = mapper.createObjectNode();
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertFalse(response.ok());
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("location"));
    }
    
    @Test
    void handle_returns_error_when_location_empty() {
        ObjectNode args = mapper.createObjectNode();
        args.put("location", "");
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertFalse(response.ok());
        assertNotNull(response.error());
        assertTrue(response.error().message().contains("location"));
    }
    
    @Test
    void handle_works_with_known_city_salt_lake_city() {
        ObjectNode args = mapper.createObjectNode();
        args.put("location", "salt lake city");
        args.put("type", "current");
        
        CallToolResponse response = tool.handle(args, ctx);
        
        // Should succeed if network is available
        // If network fails, it will return error
        assertNotNull(response);
        assertEquals("weather.query", response.toolName());
        
        if (response.ok()) {
            JsonNode result = response.result();
            assertNotNull(result);
            assertEquals("salt lake city", result.path("location").asText());
            assertTrue(result.has("current") || result.has("weather"));
        }
    }
    
    @Test
    void handle_works_with_chinese_city_name() {
        ObjectNode args = mapper.createObjectNode();
        args.put("location", "盐湖城");
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertNotNull(response);
        assertEquals("weather.query", response.toolName());
        
        if (response.ok()) {
            JsonNode result = response.result();
            assertNotNull(result);
            assertEquals("盐湖城", result.path("location").asText());
        }
    }
    
    @Test
    void handle_works_with_coordinates() {
        ObjectNode args = mapper.createObjectNode();
        args.put("location", "40.7608,-111.8910");
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertNotNull(response);
        assertEquals("weather.query", response.toolName());
    }
    
    @Test
    void handle_forecast_type() {
        ObjectNode args = mapper.createObjectNode();
        args.put("location", "new york");
        args.put("type", "forecast");
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertNotNull(response);
        assertEquals("weather.query", response.toolName());
        
        if (response.ok()) {
            JsonNode result = response.result();
            assertNotNull(result);
            assertEquals("forecast", result.path("queryType").asText());
            assertTrue(result.has("daily"));
        }
    }
    
    @Test
    void handle_hourly_type() {
        ObjectNode args = mapper.createObjectNode();
        args.put("location", "beijing");
        args.put("type", "hourly");
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertNotNull(response);
        assertEquals("weather.query", response.toolName());
        
        if (response.ok()) {
            JsonNode result = response.result();
            assertNotNull(result);
            assertEquals("hourly", result.path("queryType").asText());
            assertTrue(result.has("hourly"));
        }
    }
    
    @Test
    void handle_fahrenheit_units() {
        ObjectNode args = mapper.createObjectNode();
        args.put("location", "london");
        args.put("units", "fahrenheit");
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertNotNull(response);
        assertEquals("weather.query", response.toolName());
        
        if (response.ok()) {
            JsonNode result = response.result();
            assertNotNull(result);
            assertEquals("fahrenheit", result.path("units").asText());
        }
    }
}
