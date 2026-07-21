package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式 tool_calls 分片累积器。
 *
 * 处理 OpenAI 流式 API 中分片到达的 tool_calls delta。
 * 按 index 分组累积：每个 index 对应一个独立的工具调用，
 * 累积分片直到 finish_reason="tool_calls" 后合并为完整的 ToolCall 列表。
 *
 * <h3>流式 tool_calls 格式说明</h3>
 * 第一个 chunk 包含 id + function.name：
 * <pre>{@code
 *   {"index":0, "id":"call_abc", "type":"function", "function":{"name":"search_web","arguments":""}}
 * }</pre>
 * 后续 chunks 只有 function.arguments 分片：
 * <pre>{@code
 *   {"index":0, "function":{"arguments":"{\"qu"}}
 *   {"index":0, "function":{"arguments":"ery\":\"重庆天气\"}"}}
 * }</pre>
 * 并行调用时 index 不同：
 * <pre>{@code
 *   {"index":0, "id":"call_1", "type":"function", "function":{"name":"search_web","arguments":""}}
 *   {"index":1, "id":"call_2", "type":"function", "function":{"name":"analyze_image","arguments":""}}
 * }</pre>
 */
public class ToolCallAccumulator {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallAccumulator.class);

    private final int index;
    private final StringBuilder id;
    private final StringBuilder name;
    private final StringBuilder arguments;

    public ToolCallAccumulator(int index) {
        this.index = index;
        this.id = new StringBuilder();
        this.name = new StringBuilder();
        this.arguments = new StringBuilder();
    }

    public int getIndex() { return index; }

    /**
     * 添加一个 tool_call delta 分片。
     * 根据 JSON 中存在的字段增量追加。
     */
    public void accumulate(JsonNode delta) {
        if (delta.has("id") && !delta.get("id").isNull()) {
            id.setLength(0);
            id.append(delta.get("id").asText());
        }
        JsonNode func = delta.get("function");
        if (func != null) {
            if (func.has("name") && !func.get("name").isNull()) {
                name.setLength(0);
                name.append(func.get("name").asText());
            }
            if (func.has("arguments") && !func.get("arguments").isNull()) {
                arguments.append(func.get("arguments").asText());
            }
        }
    }

    /** 是否已收到 id 和 name（准备就绪可合并） */
    public boolean hasIdAndName() {
        return id.length() > 0 && name.length() > 0;
    }

    /** 合并为完整的 ToolCall */
    public ToolCall toToolCall() {
        return new ToolCall(id.toString(), name.toString(), arguments.toString());
    }

    @Override
    public String toString() {
        return "Accumulator[index=" + index + ", id=" + id + ", name=" + name
                + ", argsLen=" + arguments.length() + "]";
    }

    // ==================== 静态工具方法 ====================

    /**
     * 解析 delta.tool_calls JSON 数组，将分片累加到对应的 accumulators 中。
     * 如果某个 index 尚未有 accumulator，则新建。
     */
    public static void accumulateDelta(JsonNode toolCallsDelta,
                                        List<ToolCallAccumulator> accumulators) {
        if (toolCallsDelta == null || !toolCallsDelta.isArray()) return;

        for (JsonNode item : toolCallsDelta) {
            int idx = item.has("index") ? item.get("index").asInt() : -1;
            if (idx < 0) continue;

            ToolCallAccumulator acc = findOrCreate(accumulators, idx);
            acc.accumulate(item);
        }
    }

    /**
     * 将所有 accumulators 合并为最终的 ToolCall 列表。
     */
    public static List<ToolCall> finalize(List<ToolCallAccumulator> accumulators) {
        List<ToolCall> calls = new ArrayList<>();
        for (ToolCallAccumulator acc : accumulators) {
            if (acc.hasIdAndName()) {
                calls.add(acc.toToolCall());
            } else {
                logger.warn("工具调用累积器 {} 不完整（缺少 id 或 name），已忽略", acc);
            }
        }
        logger.info("合并完成 tool_calls: count={}, calls={}",
                calls.size(),
                calls.stream().map(c -> c.getName() + "(" + c.getId() + ")").toList());
        return calls;
    }

    private static ToolCallAccumulator findOrCreate(List<ToolCallAccumulator> accumulators, int index) {
        for (ToolCallAccumulator acc : accumulators) {
            if (acc.index == index) return acc;
        }
        ToolCallAccumulator newAcc = new ToolCallAccumulator(index);
        accumulators.add(newAcc);
        return newAcc;
    }
}
