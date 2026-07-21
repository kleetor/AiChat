package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具定义，对应 OpenAI API 的 tools 数组中的每个元素。
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonNode parameters;
    private final boolean strict;

    public ToolDefinition(String name, String description, JsonNode parameters) {
        this(name, description, parameters, false);
    }

    public ToolDefinition(String name, String description, JsonNode parameters, boolean strict) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.strict = strict;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public JsonNode getParameters() { return parameters; }
    public boolean isStrict() { return strict; }

    /**
     * 序列化为 OpenAI API 的 tool 格式：
     * {"type":"function","function":{"name":"...","description":"...","parameters":{...}}}
     */
    public ObjectNode toJsonNode(ObjectMapper mapper) {
        ObjectNode toolNode = mapper.createObjectNode();
        toolNode.put("type", "function");

        ObjectNode funcNode = mapper.createObjectNode();
        funcNode.put("name", name);
        funcNode.put("description", description);
        funcNode.set("parameters", parameters);
        if (strict) {
            funcNode.put("strict", true);
        }
        toolNode.set("function", funcNode);
        return toolNode;
    }
}
