package com.example.aichat.service;

import com.example.aichat.config.props.MemoryProperties;
import com.example.aichat.model.MemoryItem;
import com.example.aichat.model.MemoryItem.DetailLevel;
import com.example.aichat.model.MemoryItem.MemoryStatus;
import com.example.aichat.repository.MemoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

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

    /** Prompt injection 模式，匹配则替换为 [已过滤] */
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|directives?|commands?|prompt)" +
            "|you\\s+are\\s+(now\\s+)?(DAN|jailbreak|an?\\s+unrestricted)" +
            "|\\[system\\]|system:\\s*(override|ignore|prompt)" +
            "|<\\|im_start\\|>|<\\|im_end\\|>)",
            Pattern.DOTALL);

    private final MemoryChromaService chromaService;
    private final MemoryItemRepository memoryRepo;
    private final LLMService llmService;
    private final MemoryProperties memoryProperties;
    private final GraphMemoryService graphMemoryService;
    private final HybridRetrievalService hybridRetrievalService;

    public MemoryService(MemoryChromaService chromaService,
                         MemoryItemRepository memoryRepo,
                         LLMService llmService,
                         MemoryProperties memoryProperties,
                         GraphMemoryService graphMemoryService,
                         HybridRetrievalService hybridRetrievalService) {
        this.chromaService = chromaService;
        this.memoryRepo = memoryRepo;
        this.llmService = llmService;
        this.memoryProperties = memoryProperties;
        this.graphMemoryService = graphMemoryService;
        this.hybridRetrievalService = hybridRetrievalService;
    }

    // ==================== 模式1: 自动提取 ====================

    /**
     * 对话完成后异步提取记忆。
     * 不阻塞对话流程，异常静默忽略。
     */
    @Async
    public void extractAndStore(Long userId, Long conversationId,
                                String userMessage, String aiReply, Long promptId) {
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
                line = line.replaceFirst("^[-*\\d.·•]+\\s*", "");
                if (line.isEmpty()) continue;

                // 过滤 prompt injection 内容
                line = sanitizeMemoryValue(line);

                // 去重检查: 搜索相似记忆
                var existing = chromaService.search(userId, line, 3);
                if (!existing.isEmpty() && existing.get(0).score() > 0.85) {
                    log.debug("相似记忆已存在，跳过: {}", line);
                    continue;
                }

                String chromaId = chromaService.addMemory(userId, line,
                        Map.of("conversation_id", String.valueOf(conversationId)));

                MemoryItem item = MemoryItem.builder()
                        .userId(userId)
                        .chromaId(chromaId)
                        .value(line)
                        .originalValue(line)
                        .detailLevel(DetailLevel.FULL)
                        .source("AUTO")
                        .conversationId(conversationId)
                        .validFrom(LocalDateTime.now())
                        .promptId(promptId)
                        .build();
                item = memoryRepo.save(item);

                // 知识图谱: 提取实体、建立关系
                try {
                    graphMemoryService.linkMemory(userId, item, line);
                } catch (Exception e) {
                    log.warn("知识图谱链接失败: id={}: {}", item.getId(), e.getMessage());
                }

                // 时态管理: 检测是否与旧记忆冲突
                try {
                    detectAndResolveTemporalConflict(userId, line, item);
                } catch (Exception e) {
                    log.warn("时态冲突检测失败: id={}: {}", item.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("记忆提取失败: userId={}, convId={}", userId, conversationId, e);
        }
    }

    // ==================== 模式2: 默认注入 ====================

    /**
     * 获取注入上下文的最近记忆 (清晰期+模糊期，时间倒序）。
     * 排除已取代(SUPERSEDED)和已过期(EXPIRED)的记忆。
     * promptId 为 null 时仅返回共享记忆，非 null 时返回共享+角色专属。
     * 每条记忆在返回前执行懒衰减检查 (实时)。
     */
    public List<MemoryItem> getRecentMemoriesForContext(Long userId, Long promptId) {
        List<MemoryItem> memories = memoryRepo.findTopNActive(userId,
                List.of(DetailLevel.FULL, DetailLevel.BRIEF),
                List.of(MemoryStatus.ACTIVE),
                promptId,
                PageRequest.of(0, memoryProperties.getInject().getRecentCount(),
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
     * 用户问起历史内容时，混合检索（三路召回+RRF融合+Rerank精排）。
     * promptId 为 null 时搜索共享记忆，非 null 时搜索共享+角色专属。
     * 被查到的记忆: lastAccessedAt 归零, accessCount+1,
     * 已衰减到 BRIEF/TITLE 的恢复到 FULL (从 originalValue)。
     */
    @Transactional
    public List<MemoryItem> searchAndRecall(Long userId, String query, Long promptId) {
        try {
            List<MemoryItem> hits = hybridRetrievalService.hybridSearch(
                    userId, query, memoryProperties.getSearchTopK(), promptId);
            if (hits.isEmpty()) return List.of();

            List<MemoryItem> results = new ArrayList<>();

            for (var item : hits) {
                item.setLastAccessedAt(LocalDateTime.now());
                item.setAccessCount(item.getAccessCount() + 1);

                // 恢复: 如果已衰减，从 originalValue 回写
                if (item.getDetailLevel() != DetailLevel.FULL
                        && item.getOriginalValue() != null) {
                    item.setValue(item.getOriginalValue());
                    item.setDetailLevel(DetailLevel.FULL);
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
     *
     * 日期比较 + 简单截断为同步操作（毫秒级）；
     * LLM 智能压缩改为异步后台执行，不阻塞读取。
     *
     * @return true 表示该记忆已过期被删除
     */
    private boolean checkAndApplyDecay(MemoryItem item) {
        if (!"AUTO".equals(item.getSource())) return false; // 手动记忆不衰减
        // SUPERSEDED 的记忆不参与衰减（已在时态管理中被新事实主动取代）
        if (item.getStatus() == MemoryStatus.SUPERSEDED) return false;
        LocalDateTime now = LocalDateTime.now();
        DetailLevel level = item.getDetailLevel();

        // FULL → BRIEF: 超过 freshDays 天未访问
        if (level == DetailLevel.FULL) {
            LocalDateTime threshold = item.getLastAccessedAt().plusDays(
                    memoryProperties.getDecay().getFreshDays());
            if (now.isAfter(threshold)) {
                String fallback = truncateText(item.getValue(), 200);
                String originalValue = item.getValue();
                item.setValue(fallback);
                item.setDetailLevel(DetailLevel.BRIEF);
                memoryRepo.save(item);
                try { chromaService.updateMemory(item.getUserId(), item.getChromaId(), fallback); }
                catch (Exception e) { log.warn("ChromaDB 同步压缩失败: id={}", item.getId(), e); }
                // LLM 智能压缩异步执行，完成后回写
                compressAsync(item, originalValue, "将以下信息压缩为200字以内的摘要，保留核心事实：\n");
                log.debug("记忆衰减 FULL→BRIEF: id={}", item.getId());
                return false;
            }
        }
        // BRIEF → TITLE: 超过 briefDays 天未访问
        else if (level == DetailLevel.BRIEF) {
            LocalDateTime threshold = item.getLastAccessedAt().plusDays(
                    memoryProperties.getDecay().getBriefDays());
            if (now.isAfter(threshold)) {
                String fallback = truncateText(item.getValue(), 50);
                String originalValue = item.getValue();
                item.setValue(fallback);
                item.setDetailLevel(DetailLevel.TITLE);
                memoryRepo.save(item);
                try { chromaService.updateMemory(item.getUserId(), item.getChromaId(), fallback); }
                catch (Exception e) { log.warn("ChromaDB 同步压缩失败: id={}", item.getId(), e); }
                compressAsync(item, originalValue, "将以下信息压缩为一句话（50字以内），只保留最核心的关键词：\n");
                log.debug("记忆衰减 BRIEF→TITLE: id={}", item.getId());
                return false;
            }
        }
        // TITLE → 遗忘: 超过 forgetDays 天未访问
        else if (level == DetailLevel.TITLE) {
            LocalDateTime threshold = item.getLastAccessedAt().plusDays(
                    memoryProperties.getDecay().getForgetDays());
            if (now.isAfter(threshold)) {
                item.setStatus(MemoryStatus.EXPIRED);
                memoryRepo.save(item);
                try { chromaService.deleteMemory(item.getUserId(), item.getChromaId()); }
                catch (Exception e) { log.warn("ChromaDB 删除过期记忆失败: id={}", item.getId(), e); }
                memoryRepo.delete(item);
                log.debug("记忆已遗忘: id={}", item.getId());
                return true; // 已删除
            }
        }
        return false;
    }

    /** 简单截断文本（毫秒级，不依赖 LLM） */
    private String truncateText(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "...";
    }

    /**
     * 异步 LLM 压缩：后台调用 LLM 对已截断的记忆进行智能压缩，完成后回写。
     * 使用 CompletableFuture.runAsync 确保不阻塞当前请求，失败静默忽略。
     */
    private void compressAsync(MemoryItem item, String originalValue, String promptPrefix) {
        CompletableFuture.runAsync(() -> {
            try {
                String prompt = promptPrefix + originalValue;
                String result = llmService.chatSync(prompt);
                if (result == null || result.trim().isEmpty()) return;
                result = result.trim();
                item.setValue(result);
                memoryRepo.save(item);
                try { chromaService.updateMemory(item.getUserId(), item.getChromaId(), result); }
                catch (Exception e) { log.warn("ChromaDB 异步压缩回写失败: id={}", item.getId(), e); }
                log.debug("LLM 异步压缩完成: id={}", item.getId());
            } catch (Exception e) {
                log.warn("LLM 异步压缩失败: id={}", item.getId(), e);
            }
        });
    }

    // ==================== 时态冲突检测 ====================

    /**
     * 检测新提取的事实是否与已有记忆冲突。
     * 例：旧"用户在北京工作" vs 新"用户调到上海了"
     * → 旧记忆标记为 SUPERSEDED，新记忆正常为 ACTIVE。
     */
    private void detectAndResolveTemporalConflict(Long userId, String newFact, MemoryItem newItem) {
        // 1. ChromaDB 搜索语义相近的旧记忆
        var similar = chromaService.search(userId, newFact, 5).stream()
                .filter(h -> h.score() > 0.75 && h.score() < 0.90) // 相似但不完全相同
                .toList();

        if (similar.isEmpty()) return;

        for (var hit : similar) {
            MemoryItem oldItem = memoryRepo.findByChromaId(hit.chromaId()).orElse(null);
            if (oldItem == null || oldItem.getStatus() != MemoryStatus.ACTIVE) continue;
            if (oldItem.getId().equals(newItem.getId())) continue;

            // 2. LLM 判定是否构成冲突
            if (!isTemporalConflict(oldItem.getOriginalValue(), newFact)) continue;

            // 3. 标记旧记忆为 SUPERSEDED
            oldItem.setStatus(MemoryStatus.SUPERSEDED);
            oldItem.setValidUntil(LocalDateTime.now());
            oldItem.setSupersededById(newItem.getId());
            memoryRepo.save(oldItem);

            // 优化2: 级联失效旧记忆关联的知识图谱关系
            try {
                graphMemoryService.expireRelations(oldItem.getId(), oldItem.getUserId());
            } catch (Exception e) {
                log.warn("关系过期失败: itemId={}: {}", oldItem.getId(), e.getMessage());
            }

            log.info("时态冲突已解决: oldId={}, newId={}", oldItem.getId(), newItem.getId());
        }
    }

    /** 用 LLM 判断两条记忆是否构成同一事实的更新/取代 */
    private boolean isTemporalConflict(String oldFact, String newFact) {
        try {
            String prompt = """
                判断以下两句话是否描述同一个事实，且新信息是对旧信息的更新/取代。
                只回复 YES 或 NO。
                旧: %s
                新: %s
                """.formatted(oldFact, newFact);
            String result = llmService.chatSync(prompt);
            return "YES".equalsIgnoreCase(result != null ? result.trim() : "NO");
        } catch (Exception e) {
            log.warn("LLM 冲突判定失败", e);
            return false;
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
        // 过滤 prompt injection
        value = sanitizeMemoryValue(value);
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
        // 过滤 prompt injection
        final String sanitized = sanitizeMemoryValue(newValue);
        memoryRepo.findById(id).ifPresent(item -> {
            if (!item.getUserId().equals(userId)) return; // 归属校验
            item.setValue(sanitized);
            item.setOriginalValue(sanitized);
            item.setDetailLevel(DetailLevel.FULL);
            item.setLastAccessedAt(LocalDateTime.now());
            memoryRepo.save(item);
            chromaService.updateMemory(item.getUserId(), item.getChromaId(), sanitized);
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
            graphMemoryService.unlinkMemory(id);
            memoryRepo.delete(item);
        });
    }

    @Transactional
    public void deleteAll(Long userId) {
        memoryRepo.findByUserIdAndEnabledTrue(userId)
                .forEach(memoryRepo::delete);
        chromaService.deleteAll(userId);
    }

    /** 过滤潜在的 LLM prompt injection 模式 */
    static String sanitizeMemoryValue(String value) {
        if (value == null) return null;
        return INJECTION_PATTERN.matcher(value).replaceAll("[已过滤]");
    }
}
