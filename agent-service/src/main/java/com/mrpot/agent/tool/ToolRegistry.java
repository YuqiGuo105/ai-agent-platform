package com.mrpot.agent.tool;

import com.mrpot.agent.common.replay.ReplayMode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, ToolMetadata> tools = new HashMap<>();

    @PostConstruct
    public void init() {
        register("kb.search", true, false, false);
        register("file.understandUrl", true, false, false);
        register("system.ping", true, false, false);
        register("system.time", true, false, false);
        register("redis.get", true, false, false);
        register("http.get", true, false, false);
        register("reasoning.analyze", true, false, false);
        register("reasoning.compare", true, false, false);
        register("memory.store", false, true, false);
        register("memory.recall", true, false, false);
        register("web_search", false, true, false);
        register("send_email", false, true, true);
        register("http.post", false, true, false);
        register("http.put", false, true, false);
        register("http.delete", false, true, true);
    }

    public void register(String name, boolean replayable, boolean hasSideEffects, boolean requiresPermission) {
        tools.put(name, new ToolMetadata(name, replayable, hasSideEffects, requiresPermission));
    }

    public ToolMetadata getMetadata(String toolName) {
        return tools.get(toolName);
    }

    public boolean isReplayable(String toolName, ReplayMode mode, List<String> allowedTools) {
        if (allowedTools != null && !allowedTools.isEmpty()) {
            if (!allowedTools.contains(toolName)) {
                return false;
            }
        }

        ToolMetadata metadata = tools.get(toolName);
        if (metadata == null) {
            return false;
        }

        return switch (mode) {
            case FULL -> metadata.isReplayable();
            case TOOLS_ONLY -> metadata.isReplayable();
            case LLM_ONLY -> false;
        };
    }
}
