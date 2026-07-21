package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心。
 * 通过 Spring 自动收集所有 {@link ToolHandler} 实现，提供工具查找和执行能力。
 */
@Component
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ToolRegistry(ObjectMapper objectMapper, List<ToolHandler> handlerList) {
        this.objectMapper = objectMapper;
        for (ToolHandler handler : handlerList) {
            String name = handler.name();
            if (handlers.containsKey(name)) {
                logger.warn("重复的工具名 '{}'，后注册的将覆盖先前的", name);
            }
            handlers.put(name, handler);
            logger.info("注册工具: {}", name);
        }
    }

    /**
     * 根据调用请求执行对应工具。
     */
    public ToolResult execute(ToolCall call) {
        ToolHandler handler = handlers.get(call.getName());
        if (handler == null) {
            logger.warn("未找到工具: {}", call.getName());
            return new ToolResult(call.getId(), call.getName(),
                    "工具 '" + call.getName() + "' 不可用");
        }
        try {
            return handler.execute(call);
        } catch (Exception e) {
            logger.error("工具 '{}' 执行失败", call.getName(), e);
            return new ToolResult(call.getId(), call.getName(),
                    "工具 '" + call.getName() + "' 执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前应激活的工具定义列表。
     *
     * @param webSearchEnabled 是否启用联网搜索
     * @param hasImageUrl      用户是否上传了图片
     * @return 激活的 ToolDefinition 列表
     */
    public List<ToolDefinition> getActiveTools(boolean webSearchEnabled, boolean hasImageUrl) {
        List<ToolDefinition> tools = new ArrayList<>();
        if (webSearchEnabled) {
            ToolHandler h = handlers.get("search_web");
            if (h != null) tools.add(h.getDefinition());
        }
        if (hasImageUrl) {
            ToolHandler h = handlers.get("analyze_image");
            if (h != null) tools.add(h.getDefinition());
        }
        return tools;
    }

    /**
     * 是否有任何工具被激活。
     */
    public boolean hasActiveTools(boolean webSearchEnabled, boolean hasImageUrl) {
        return (webSearchEnabled && handlers.containsKey("search_web"))
                || (hasImageUrl && handlers.containsKey("analyze_image"));
    }

    /**
     * 返回所有已注册工具的名称列表（用于调试）。
     */
    public List<String> getRegisteredToolNames() {
        return Collections.unmodifiableList(new ArrayList<>(handlers.keySet()));
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
