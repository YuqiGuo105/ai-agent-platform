package com.mrpot.agent.tools.service;

import com.mrpot.agent.common.tool.mcp.ToolDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolRegistry {
  private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();

  public ToolRegistry(List<ToolHandler> handlerList) {
    handlerList.forEach(h -> handlers.put(h.name(), h));
  }

  public List<ToolDefinition> listTools() {
    return handlers.values().stream()
        .map(ToolHandler::definition)
        .toList();
  }

  public ToolHandler getHandler(String toolName) {
    return handlers.get(toolName);
  }
}
