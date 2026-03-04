package com.mrpot.agent.tools.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mrpot.agent.common.tool.mcp.CallToolResponse;
import com.mrpot.agent.tools.service.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemPingToolTest {

    private SystemPingTool tool;
    private ObjectMapper mapper;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        tool = new SystemPingTool();
        mapper = new ObjectMapper();
        ctx = new ToolContext("trace-1", "session-1");
    }

    @Test
    void name_returns_system_ping() {
        assertEquals("system.ping", tool.name());
    }

    @Test
    void definition_returns_valid_definition() {
        var def = tool.definition();
        assertNotNull(def);
        assertEquals("system.ping", def.name());
        assertEquals("1.0.0", def.version());
        assertNotNull(def.description());
    }

    @Test
    void handle_returns_ok_response() {
        ObjectNode args = mapper.createObjectNode();
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertTrue(response.ok());
        assertEquals("system.ping", response.toolName());
        assertNotNull(response.result());
        assertTrue(response.result().has("ok"));
        assertTrue(response.result().get("ok").asBoolean());
        assertTrue(response.result().has("timestamp"));
        assertTrue(response.result().has("version"));
    }

    @Test
    void handle_includes_memory_info() {
        ObjectNode args = mapper.createObjectNode();
        
        CallToolResponse response = tool.handle(args, ctx);
        
        assertTrue(response.result().has("freeMemoryMB"));
        assertTrue(response.result().has("totalMemoryMB"));
        assertTrue(response.result().get("freeMemoryMB").asLong() > 0);
    }
}
