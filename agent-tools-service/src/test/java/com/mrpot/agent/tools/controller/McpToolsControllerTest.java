package com.mrpot.agent.tools.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrpot.agent.common.api.ScopeMode;
import com.mrpot.agent.common.api.ToolProfile;
import com.mrpot.agent.common.tool.mcp.CallToolRequest;
import com.mrpot.agent.common.tool.mcp.ListToolsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class McpToolsControllerTest {
  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper mapper;

  @Test
  void list_tools_ok() throws Exception {
    var req = new ListToolsRequest(ScopeMode.AUTO, ToolProfile.DEFAULT, "trace", "sess");
    mockMvc.perform(post("/mcp/list_tools")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tools.length()").value(2));
  }

  @Test
  void call_tool_ok_and_not_found() throws Exception {
    var req = new CallToolRequest("system.ping", mapper.createObjectNode(), ScopeMode.AUTO,
        ToolProfile.DEFAULT, "trace", "sess");
    mockMvc.perform(post("/mcp/call_tool").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    var missing = new CallToolRequest("missing", mapper.createObjectNode(), ScopeMode.AUTO,
        ToolProfile.DEFAULT, "trace", "sess");
    mockMvc.perform(post("/mcp/call_tool").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(missing)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
