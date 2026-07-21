package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolDefinition JSON 序列化测试。
 */
class ToolDefinitionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("toJsonNode → 正确的 OpenAI tools 格式")
    void toJsonNodeCorrectFormat() {
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");
        ObjectNode props = mapper.createObjectNode();
        ObjectNode queryProp = mapper.createObjectNode();
        queryProp.put("type", "string");
        props.set("query", queryProp);
        params.set("properties", props);

        ToolDefinition def = new ToolDefinition("search_web", "搜索互联网获取实时信息", params);

        ObjectNode result = def.toJsonNode(mapper);
        assertEquals("function", result.get("type").asText());

        ObjectNode func = (ObjectNode) result.get("function");
        assertNotNull(func);
        assertEquals("search_web", func.get("name").asText());
        assertEquals("搜索互联网获取实时信息", func.get("description").asText());
        assertNotNull(func.get("parameters"));
        assertEquals("object", func.get("parameters").get("type").asText());
    }

    @Test
    @DisplayName("strict=false → function 中不含 strict 字段")
    void nonStrictOmitsStrictField() {
        ObjectNode params = mapper.createObjectNode().put("type", "object");
        ToolDefinition def = new ToolDefinition("test", "desc", params, false);

        ObjectNode func = (ObjectNode) def.toJsonNode(mapper).get("function");
        assertFalse(func.has("strict"));
    }

    @Test
    @DisplayName("strict=true → function 含 strict:true")
    void strictIncludesStrictField() {
        ObjectNode params = mapper.createObjectNode().put("type", "object");
        ToolDefinition def = new ToolDefinition("test", "desc", params, true);

        ObjectNode func = (ObjectNode) def.toJsonNode(mapper).get("function");
        assertTrue(func.has("strict"));
        assertTrue(func.get("strict").asBoolean());
    }
}
