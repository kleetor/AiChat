package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolCallAccumulator 单元测试。
 * 验证流式 tool_calls delta 分片累积的正确性。
 */
class ToolCallAccumulatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ==================== 单工具累积测试 ====================

    @Test
    @DisplayName("单工具: id + name + arguments 分片到达 → 正确合并")
    void singleToolBasicFlow() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();

        // chunk1: id + name 出现
        accumulate(accumulators, tcDelta(0, "call_abc", "search_web", ""));
        assertEquals(1, accumulators.size());
        assertEquals(0, accumulators.get(0).getIndex());

        // chunk2: arguments 分片1
        accumulate(accumulators, tcDelta(0, null, null, "{\"qu"));

        // chunk3: arguments 分片2
        accumulate(accumulators, tcDelta(0, null, null, "ery\":\"2026年7月 天气\"}"));

        List<ToolCall> calls = ToolCallAccumulator.finalize(accumulators);
        assertEquals(1, calls.size());
        assertEquals("call_abc", calls.get(0).getId());
        assertEquals("search_web", calls.get(0).getName());
        assertEquals("{\"query\":\"2026年7月 天气\"}", calls.get(0).getArguments());
    }

    @Test
    @DisplayName("单工具: id + name + arguments 一次性到达 → 正确合并")
    void singleToolAllAtOnce() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();

        accumulate(accumulators, tcDelta(0, "call_x1", "search_web", "{\"query\":\"hello\"}"));

        List<ToolCall> calls = ToolCallAccumulator.finalize(accumulators);
        assertEquals(1, calls.size());
        assertEquals("call_x1", calls.get(0).getId());
        assertEquals("search_web", calls.get(0).getName());
        assertEquals("{\"query\":\"hello\"}", calls.get(0).getArguments());
    }

    // ==================== 并行工具累积测试 ====================

    @Test
    @DisplayName("并行工具: 两个工具交替到达 → 按 index 正确分离")
    void parallelToolsInterleaved() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();

        // index=0 的 id+name
        accumulate(accumulators, tcDelta(0, "call_1", "search_web", ""));
        // index=1 的 id+name
        accumulate(accumulators, tcDelta(1, "call_2", "analyze_image", ""));
        assertEquals(2, accumulators.size());

        // index=0 arguments 分片
        accumulate(accumulators, tcDelta(0, null, null, "{\"query\":\"天气\"}"));
        // index=1 arguments 分片
        accumulate(accumulators, tcDelta(1, null, null, "{\"image_url\":\"https://s3.example.com/img.png\"}"));

        List<ToolCall> calls = ToolCallAccumulator.finalize(accumulators);
        assertEquals(2, calls.size());

        assertEquals("call_1", calls.get(0).getId());
        assertEquals("search_web", calls.get(0).getName());
        assertEquals("{\"query\":\"天气\"}", calls.get(0).getArguments());

        assertEquals("call_2", calls.get(1).getId());
        assertEquals("analyze_image", calls.get(1).getName());
        assertEquals("{\"image_url\":\"https://s3.example.com/img.png\"}", calls.get(1).getArguments());
    }

    @Test
    @DisplayName("并行工具: 两个工具同时到达 → 正确分离")
    void parallelToolsSimultaneous() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();

        // 一个 chunk 包含两个 tool_calls 的 id+name+arguments
        ObjectNode root = mapper.createObjectNode();
        ArrayNode tcArray = mapper.createArrayNode();
        tcArray.add(tcDeltaObj(0, "call_a", "search_web", "{\"query\":\"新闻\"}"));
        tcArray.add(tcDeltaObj(1, "call_b", "analyze_image", "{\"image_url\":\"https://img.com/p.png\"}"));
        root.set("tool_calls", tcArray);

        ToolCallAccumulator.accumulateDelta(root.get("tool_calls"), accumulators);
        assertEquals(2, accumulators.size());

        List<ToolCall> calls = ToolCallAccumulator.finalize(accumulators);
        assertEquals(2, calls.size());
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("empty tool_calls delta → accumulator 列表不变")
    void emptyDeltaIgnored() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();
        ToolCallAccumulator.accumulateDelta(null, accumulators);
        assertTrue(accumulators.isEmpty());

        ObjectNode root = mapper.createObjectNode();
        root.putArray("tool_calls");
        ToolCallAccumulator.accumulateDelta(root.get("tool_calls"), accumulators);
        assertTrue(accumulators.isEmpty());
    }

    @Test
    @DisplayName("无 index 的 tool_call delta → 被忽略")
    void noIndexDeltaIgnored() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();
        accumulate(accumulators, tcDelta(-1, "call_z", "test_tool", "{}"));
        assertTrue(accumulators.isEmpty(),
                "没有 index 的 delta 应该被忽略");
    }

    @Test
    @DisplayName("缺少 id 或 name 的 accumulator → finalize 时被忽略")
    void incompleteAccumulatorSkipped() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();

        // 只有 arguments，没有 id 和 name —— 这种情况在 OpenAI API 中不会发生，但应安全处理
        // 实际不会发生，但测试防御性代码
        ToolCallAccumulator acc = new ToolCallAccumulator(0);
        accumulators.add(acc);
        // 不调用任何 accumulate

        List<ToolCall> calls = ToolCallAccumulator.finalize(accumulators);
        assertTrue(calls.isEmpty(), "未收到 id/name 的 accumulator 应被忽略");
    }

    @Test
    @DisplayName("多次接收同一个 index 的 id → 最后一次覆盖")
    void idOverwrite() {
        List<ToolCallAccumulator> accumulators = new ArrayList<>();
        ToolCallAccumulator acc = new ToolCallAccumulator(0);
        accumulators.add(acc);

        acc.accumulate(tcDeltaObj(0, "call_v1", "search_web", ""));
        acc.accumulate(tcDeltaObj(0, "call_v2", null, ""));
        // id 应被覆盖为 call_v2

        assertEquals("call_v2", acc.toToolCall().getId());
    }

    // ==================== 辅助方法 ====================

    private void accumulate(List<ToolCallAccumulator> accumulators, ObjectNode deltaObj) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode tcArray = mapper.createArrayNode();
        tcArray.add(deltaObj);
        root.set("tool_calls", tcArray);
        ToolCallAccumulator.accumulateDelta(root.get("tool_calls"), accumulators);
    }

    /**
     * 构建一个符合 OpenAI 格式的 tool_call delta JSON 对象。
     */
    private ObjectNode tcDeltaObj(int index, String id, String name, String arguments) {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("index", index);
        if (id != null) obj.put("id", id);

        ObjectNode func = mapper.createObjectNode();
        if (name != null) func.put("name", name);
        if (arguments != null) func.put("arguments", arguments);
        if (func.size() > 0) obj.set("function", func);

        return obj;
    }

    /** 便捷方法：创建有 index 的 tc delta */
    private ObjectNode tcDelta(int index, String id, String name, String arguments) {
        return tcDeltaObj(index, id, name, arguments);
    }
}
