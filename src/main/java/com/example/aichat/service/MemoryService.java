package com.example.aichat.service;

import com.example.aichat.model.MemoryItem;
import com.example.aichat.model.MemoryItem.DetailLevel;
import com.example.aichat.repository.MemoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆业务逻辑 — 人类记忆模型的四种操作模式。
 *
 * 模式1: 自动提取 — AI回复后异步提取关键事实
 * 模式2: 默认注入 — 时间倒序最近N条清晰/模糊期记忆
 * 模式3: 按需回溯 — 语义搜索全库 (触发时机由调用方控制)
 * 模式4: 懒衰减 — 读取记忆时实时检查并执行阶梯衰减
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryChromaService chromaService;
    private final MemoryItemRepository memoryRepo;
    private final LLMService llmService;

    @Value("${memory.inject.recent-count:20}")
    private int injectRecentCount;

    @Value("${memory.search.top-k:10}")
    private int searchTopK;

    @Value("${memory.decay.fresh-days:3}")
    private int freshDays;

    @Value("${memory.decay.brief-days:7}")
    private int briefDays;

    @Value("${memory.decay.forget-days:14}")
    private int forgetDays;

    public MemoryService(MemoryChromaService chromaService,
                         MemoryItemRepository memoryRepo,
                         LLMService llmService) {
        this.chromaService = chromaService;
        this.memoryRepo = memoryRepo;
        this.llmService = llmService;
    }

    // ==================== 模式1: 自动提取 ====================

    /**
     * 对话完成后异步提取记忆。
     * 不阻塞对话流程，异常静默忽略。
     */
    @Async
    public void extractAndStore(Long userId, Long conversationId,
                                String userMessage, String aiReply) {
        try {
            String prompt = """
                从以下对话中提取关于用户的值得长期记住的关键信息。
                规则:
                - 只提取有价值的事实、偏好、习惯、重要事件
                - 每行一条，直接写事实描述，不加编号和符号前缀
                - 闲聊、问候等无信息量的对话回复 "NONE"

                用户: %s
                AI: %s
                """.formatted(userMessage, aiReply);

            String result = llmService.chatSync(prompt);
            if (result == null || "NONE".equals(result.trim())) return;

            for (String line : result.split("\n")) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("NONE")) continue;
                // 去格式前缀 "- " "1. " "·" 等
                line = line.replaceFirst("^[-*\\d.·•]+\\s*", "");
                if (line.isEmpty()) continue;

                // 去重检查: 搜索相似记忆
                var existing = chromaService.search(userId, line, 3);
                if (!existing.isEmpty() && existing.get(0).score() > 0.85) {
                    log.debug("相似记忆已存在，跳过: {}", line);
                    continue;
                }

                String chromaId = chromaService.addMemory(userId, line,
                        Map.of("conversation_id", String.valueOf(conversationId)));

                memoryRepo.save(MemoryItem.builder()
                        .userId(userId)
                        .chromaId(chromaId)
                        .value(line)
                        .originalValue(line)
                        .detailLevel(DetailLevel.FULL)
                        .source("AUTO")
                        .conversationId(conversationId)
                        .build());
            }
        } catch (Exception e) {
            log.warn("记忆提取失败: userId={}, convId={}", userId, conversationId, e);
        }
    }

    // ==================== 模式2: 默认注入 ====================

    /**
     * 获取注入上下文的最近记忆 (清晰期+模糊期，时间倒序)。
     * 每条记忆在返回前执行懒衰减检查 (实时)。
     */
    public List<MemoryItem> getRecentMemoriesForContext(Long userId) {
        List<MemoryItem> memories = memoryRepo.findTopNEnabled(userId,
                List.of(DetailLevel.FULL, DetailLevel.BRIEF),
                PageRequest.of(0, injectRecentCount,
                        Sort.by(Sort.Direction.DESC, "lastAccessedAt")));

        // 懒衰减: 每条记忆检查是否该降级/删除
        return memories.stream()
                .filter(m -> !checkAndApplyDecay(m))  // 被删除的过滤掉
                .toList();
    }

    /**
     * 批量刷新记忆的 lastAccessedAt (注入即访问)。
     */
    @Transactional
    public void touchMemories(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        try {
            memoryRepo.batchTouch(ids, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("批量刷新记忆访问时间失败", e);
        }
    }

    // ==================== 模式3: 按需回溯 ====================

    /**
     * 用户问起历史内容时，语义搜索全库并刷新访问状态。
     * 被查到的记忆: lastAccessedAt 归零, accessCount+1,
     * 已衰减到 BRIEF/TITLE 的恢复到 FULL (从 originalValue)。
     */
    @Transactional
    public List<MemoryItem> searchAndRecall(Long userId, String query) {
        try {
            var hits = chromaService.search(userId, query, searchTopK);
            if (hits.isEmpty()) return List.of();

            List<String> chromaIds = hits.stream()
                    .map(MemoryChromaService.MemoryHit::chromaId).toList();
            List<MemoryItem> items = memoryRepo.findByChromaIdIn(chromaIds);
            List<MemoryItem> results = new ArrayList<>();

            for (var item : items) {
                item.setLastAccessedAt(LocalDateTime.now());
                item.setAccessCount(item.getAccessCount() + 1);

                // 恢复: 如果已衰减，从 originalValue 回写
                if (item.getDetailLevel() != DetailLevel.FULL
                        && item.getOriginalValue() != null) {
                    item.setValue(item.getOriginalValue());
                    item.setDetailLevel(DetailLevel.FULL);
                    // 同步 ChromaDB
                    chromaService.updateMemory(userId, item.getChromaId(), item.getOriginalValue());
                }
                memoryRepo.save(item);
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("记忆回溯失败: userId={}", userId, e);
            return List.of();
        }
    }

    // ==================== 模式4: 懒衰减 (访问时实时检查) ====================

    /**
     * 懒衰减: 在记忆被读取时检查是否该降级/删除。
     * 分摊成本到每次请求，而非凌晨集中批量执行。
     * @return true 表示该记忆已过期被删除
     */
    private boolean checkAndApplyDecay(MemoryItem item) {
        if (!"AUTO".equals(item.getSource())) return false; // 手动记忆不衰减
        LocalDateTime now = LocalDateTime.now();
        DetailLevel level = item.getDetailLevel();

        // FULL → BRIEF: 超过 freshDays 天未访问
        if (level == DetailLevel.FULL) {
            LocalDateTime threshold = item.getLastAccessedAt().plusDays(freshDays);
            if (now.isAfter(threshold)) {
                String compressed = compressIfNeeded(item.getValue(), "将以下信息压缩为200字以内的摘要，保留核心事实：\n");
                if (compressed != null) {
                    item.setValue(compressed);
                    item.setDetailLevel(DetailLevel.BRIEF);
                    memoryRepo.save(item);
                    try { chromaService.updateMemory(item.getUserId(), item.getChromaId(), compressed); }
                    catch (Exception e) { log.warn("ChromaDB 同步压缩失败: id={}", item.getId(), e); }
                    log.debug("记忆衰减 FULL→BRIEF: id={}", item.getId());
                }
                return false;
            }
        }
        // BRIEF → TITLE: 超过 briefDays 天未访问
        else if (level == DetailLevel.BRIEF) {
            LocalDateTime threshold = item.getLastAccessedAt().plusDays(briefDays);
            if (now.isAfter(threshold)) {
                String compressed = compressIfNeeded(item.getValue(), "将以下信息压缩为一句话（50字以内），只保留最核心的关键词：\n");
                if (compressed != null) {
                    item.setValue(compressed);
                    item.setDetailLevel(DetailLevel.TITLE);
                    memoryRepo.save(item);
                    try { chromaService.updateMemory(item.getUserId(), item.getChromaId(), compressed); }
                    catch (Exception e) { log.warn("ChromaDB 同步压缩失败: id={}", item.getId(), e); }
                    log.debug("记忆衰减 BRIEF→TITLE: id={}", item.getId());
                }
                return false;
            }
        }
        // TITLE → 遗忘: 超过 forgetDays 天未访问
        else if (level == DetailLevel.TITLE) {
            LocalDateTime threshold = item.getLastAccessedAt().plusDays(forgetDays);
            if (now.isAfter(threshold)) {
                try { chromaService.deleteMemory(item.getUserId(), item.getChromaId()); }
                catch (Exception e) { log.warn("ChromaDB 删除过期记忆失败: id={}", item.getId(), e); }
                memoryRepo.delete(item);
                log.debug("记忆已遗忘: id={}", item.getId());
                return true; // 已删除
            }
        }
        return false;
    }

    /** 压缩文本 (≤50字直接截断，不做 LLM 调用) */
    private String compressIfNeeded(String text, String promptPrefix) {
        if (text.length() <= 50) return text; // 短文本直接复用
        return compressWithLLM(promptPrefix + text);
    }

    private String compressWithLLM(String prompt) {
        try {
            String result = llmService.chatSync(prompt);
            return (result != null) ? result.trim() : null;
        } catch (Exception e) {
            log.warn("LLM压缩失败", e);
            return null;
        }
    }

    // ==================== 手动 CRUD ====================

    public List<MemoryItem> listAll(Long userId) {
        return memoryRepo.findByUserIdOrderByLastAccessedAtDesc(userId)
                .stream()
                .filter(m -> !checkAndApplyDecay(m)) // 懒衰减
                .toList();
    }

    public List<MemoryItem> listEnabled(Long userId) {
        return memoryRepo.findByUserIdAndEnabledTrue(userId)
                .stream()
                .filter(m -> !checkAndApplyDecay(m)) // 懒衰减
                .toList();
    }

    public MemoryItem addManual(Long userId, String value) {
        String chromaId = chromaService.addMemory(userId, value, Map.of("source", "manual"));
        return memoryRepo.save(MemoryItem.builder()
                .userId(userId)
                .chromaId(chromaId)
                .value(value)
                .originalValue(value)
                .detailLevel(DetailLevel.FULL)
                .source("MANUAL")
                .build());
    }

    @Transactional
    public void update(Long id, Long userId, String newValue) {
        memoryRepo.findById(id).ifPresent(item -> {
            if (!item.getUserId().equals(userId)) return; // 归属校验
            item.setValue(newValue);
            item.setOriginalValue(newValue);
            item.setDetailLevel(DetailLevel.FULL);
            item.setLastAccessedAt(LocalDateTime.now());
            memoryRepo.save(item);
            chromaService.updateMemory(item.getUserId(), item.getChromaId(), newValue);
        });
    }

    @Transactional
    public void toggleEnabled(Long id, Long userId, boolean enabled) {
        memoryRepo.findById(id).ifPresent(item -> {
            if (!item.getUserId().equals(userId)) return; // 归属校验
            item.setEnabled(enabled);
            memoryRepo.save(item);
        });
    }

    @Transactional
    public void delete(Long id, Long userId) {
        memoryRepo.findById(id).ifPresent(item -> {
            if (!item.getUserId().equals(userId)) return; // 归属校验
            chromaService.deleteMemory(item.getUserId(), item.getChromaId());
            memoryRepo.delete(item);
        });
    }

    @Transactional
    public void deleteAll(Long userId) {
        chromaService.deleteAll(userId);
        // MySQL 侧: 删除该用户所有已启用的记忆记录
        memoryRepo.findByUserIdAndEnabledTrue(userId)
                .forEach(memoryRepo::delete);
    }
}
