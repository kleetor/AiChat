package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 注册与激活逻辑测试。
 */
class ToolRegistryTest {

    private ToolRegistry registry;
    private final FakeHandler searchHandler = new FakeHandler("search_web");
    private final FakeHandler imageHandler = new FakeHandler("analyze_image");

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(new ObjectMapper(), List.of(searchHandler, imageHandler));
    }

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("两个 handler 正确注册")
    void bothHandlersRegistered() {
        List<String> names = registry.getRegisteredToolNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("search_web"));
        assertTrue(names.contains("analyze_image"));
    }

    // ==================== 激活逻辑 ====================

    @Test
    @DisplayName("webSearchEnabled=true, hasImageUrl=false → 仅激活 search_web")
    void activeToolsSearchOnly() {
        List<ToolDefinition> tools = registry.getActiveTools(true, false);
        assertEquals(1, tools.size());
        assertEquals("search_web", tools.get(0).getName());
    }

    @Test
    @DisplayName("webSearchEnabled=false, hasImageUrl=true → 仅激活 analyze_image")
    void activeToolsImageOnly() {
        List<ToolDefinition> tools = registry.getActiveTools(false, true);
        assertEquals(1, tools.size());
        assertEquals("analyze_image", tools.get(0).getName());
    }

    @Test
    @DisplayName("webSearchEnabled=true, hasImageUrl=true → 激活两个工具")
    void activeToolsBoth() {
        List<ToolDefinition> tools = registry.getActiveTools(true, true);
        assertEquals(2, tools.size());
    }

    @Test
    @DisplayName("webSearchEnabled=false, hasImageUrl=false → 无激活工具")
    void activeToolsNone() {
        List<ToolDefinition> tools = registry.getActiveTools(false, false);
        assertTrue(tools.isEmpty());
    }

    @Test
    @DisplayName("hasActiveTools 各种组合")
    void hasActiveToolsCombinations() {
        assertTrue(registry.hasActiveTools(true, false));
        assertTrue(registry.hasActiveTools(false, true));
        assertTrue(registry.hasActiveTools(true, true));
        assertFalse(registry.hasActiveTools(false, false));
    }

    // ==================== 执行测试 ====================

    @Test
    @DisplayName("execute 正确分发到对应 handler")
    void executeDispatchesCorrectly() {
        ToolCall call = new ToolCall("call_1", "search_web", "{\"query\":\"test\"}");
        ToolResult result = registry.execute(call);

        assertEquals("call_1", result.getToolCallId());
        assertEquals("search_web", result.getName());
        assertTrue(result.getContent().contains("search_web"));
    }

    @Test
    @DisplayName("execute 未注册的工具 → 返回错误信息")
    void executeUnknownTool() {
        ToolCall call = new ToolCall("call_99", "unknown_tool", "{}");
        ToolResult result = registry.execute(call);

        assertEquals("call_99", result.getToolCallId());
        assertEquals("unknown_tool", result.getName());
        assertTrue(result.getContent().contains("不可用"));
    }

    // ==================== 不存在 handler 时的降级 ====================

    @Test
    @DisplayName("handler 不存在时 getActiveTools 不抛异常，返回空或跳过")
    void getActiveToolsWithMissingHandler() {
        // 只注册 search_web，analyze_image 不存在
        ToolRegistry registryOnlySearch = new ToolRegistry(new ObjectMapper(), List.of(searchHandler));

        List<ToolDefinition> tools = registryOnlySearch.getActiveTools(true, true);
        assertEquals(1, tools.size(), "analyze_image 不存在时应跳过");
        assertEquals("search_web", tools.get(0).getName());

        assertTrue(registryOnlySearch.hasActiveTools(true, false));
        assertFalse(registryOnlySearch.hasActiveTools(false, true),
                "analyze_image 未注册 → hasActiveTools(无搜索, 有图片) 应返回 false");
    }

    // ==================== 并发执行测试 ====================

    @Test
    @DisplayName("并发执行两个工具 → 总耗时接近最慢的工具")
    void parallelExecutionFasterThanSequential() throws Exception {
        FakeHandler slow1 = new FakeHandler("slow_1", 200); // 200ms
        FakeHandler slow2 = new FakeHandler("slow_2", 200); // 200ms
        ToolRegistry reg = new ToolRegistry(new ObjectMapper(), List.of(slow1, slow2));

        ToolCall call1 = new ToolCall("c1", "slow_1", "{}");
        ToolCall call2 = new ToolCall("c2", "slow_2", "{}");

        long start = System.currentTimeMillis();
        CompletableFuture<ToolResult> f1 = CompletableFuture.supplyAsync(() -> reg.execute(call1));
        CompletableFuture<ToolResult> f2 = CompletableFuture.supplyAsync(() -> reg.execute(call2));
        CompletableFuture.allOf(f1, f2).join();

        ToolResult r1 = f1.get();
        ToolResult r2 = f2.get();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("c1", r1.getToolCallId());
        assertEquals("c2", r2.getToolCallId());
        // 并发执行耗时 ≈ 200ms，串行 ≈ 400ms
        assertTrue(elapsed < 350, "并发执行应远快于串行，实际: " + elapsed + "ms");
    }

    @Test
    @DisplayName("并发执行中一个工具失败 → 另一个正常完成")
    void parallelExecutionOneFails() throws Exception {
        FakeHandler normal = new FakeHandler("normal", 50);
        FakeHandler failing = new FakeHandler("failing", 10) {
            @Override
            public ToolResult execute(ToolCall call) {
                throw new RuntimeException("simulated failure");
            }
        };
        ToolRegistry reg = new ToolRegistry(new ObjectMapper(), List.of(normal, failing));

        ToolCall call1 = new ToolCall("c1", "normal", "{}");
        ToolCall call2 = new ToolCall("c2", "failing", "{}");

        // ToolRegistry.execute 会 catch 异常并返回错误 ToolResult
        CompletableFuture<ToolResult> f1 = CompletableFuture.supplyAsync(() -> reg.execute(call1));
        CompletableFuture<ToolResult> f2 = CompletableFuture.supplyAsync(() -> reg.execute(call2));
        CompletableFuture.allOf(f1, f2).join();

        ToolResult r1 = f1.get();
        ToolResult r2 = f2.get();

        assertEquals("c1", r1.getToolCallId());
        assertTrue(r1.getContent().contains("Fake result from normal"));

        assertEquals("c2", r2.getToolCallId());
        assertTrue(r2.getContent().contains("simulated failure"),
                "失败的工具应返回错误信息，实际: " + r2.getContent());
    }

    // ==================== 辅助类 ====================

    private static class FakeHandler implements ToolHandler {
        private final String name;
        private final long delayMs;

        FakeHandler(String name) { this(name, 0); }

        FakeHandler(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }

        @Override
        public String name() { return name; }

        @Override
        public ToolDefinition getDefinition() {
            return new ToolDefinition(
                    name, name + " description",
                    new ObjectMapper().createObjectNode().put("type", "object"));
        }

        @Override
        public ToolResult execute(ToolCall call) {
            if (delayMs > 0) {
                try { TimeUnit.MILLISECONDS.sleep(delayMs); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return new ToolResult(call.getId(), name,
                    "Fake result from " + name + ": " + call.getArguments());
        }
    }
}
