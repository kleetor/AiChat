package com.example.aichat.controller.admin;

import com.example.aichat.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 管理后台 API 健康检查控制器。
 * 直接调用 Service 层方法进行测试，仅覆盖只读 GET 端点（避免写操作的副作用）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiTestController {

    private static final Logger logger = LoggerFactory.getLogger(AdminApiTestController.class);

    private final AdminService adminService;
    private final AdminPromptService adminPromptService;
    private final AdminSystemRuleService adminSystemRuleService;
    private final SponsorReviewService sponsorReviewService;
    private final ConversationService conversationService;
    private final BillingService billingService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptsHubService promptsHubService;

    public AdminApiTestController(AdminService adminService,
                                   AdminPromptService adminPromptService,
                                   AdminSystemRuleService adminSystemRuleService,
                                   SponsorReviewService sponsorReviewService,
                                   ConversationService conversationService,
                                   BillingService billingService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   PromptsHubService promptsHubService) {
        this.adminService = adminService;
        this.adminPromptService = adminPromptService;
        this.adminSystemRuleService = adminSystemRuleService;
        this.sponsorReviewService = sponsorReviewService;
        this.conversationService = conversationService;
        this.billingService = billingService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.promptsHubService = promptsHubService;
    }

    @PostMapping("/api-test")
    public ResponseEntity<List<Map<String, Object>>> testApis(@RequestAttribute("userId") Long adminId) {
        List<Map<String, Object>> results = new ArrayList<>();

        // ======== 仪表盘 ========
        testOne(results, "仪表盘统计", "dashboard", () -> adminService.getDashboardStats());
        testOne(results, "图表数据(7天)", "dashboard", () -> adminService.getChartData(7));

        // ======== 用户管理 ========
        testOne(results, "用户列表", "users", () -> adminService.getUsers(null, 0, 5, "id", "desc"));

        // ======== 赞助审核 ========
        testOne(results, "赞助审核列表", "sponsors", () ->
                sponsorReviewService.getSponsorReviews("PENDING", 0, 5));

        // ======== 提示词审核 ========
        testOne(results, "提示词审核队列", "prompt-audit", () ->
                adminPromptService.getAuditQueue("pending_review", 0, 5));

        // ======== 社区管理 ========
        testOne(results, "社区提示词列表", "prompts", () ->
                adminPromptService.getPromptsHub(null, 0, 5));
        testOne(results, "社区提示词搜索", "prompts", () ->
                adminPromptService.getPromptsHub("测试", 0, 5));

        // ======== 模型配置 ========
        testOne(results, "模型配置列表", "models", () -> adminService.getModelConfigs());

        // ======== 系统规则 ========
        testOne(results, "系统规则列表", "rules", () -> adminSystemRuleService.list());

        // ======== 消费记录 ========
        testOne(results, "消费记录", "usage", () ->
                adminService.getUsageRecords(null, null, null, 0, 5));
        testOne(results, "收入统计", "usage", () ->
                adminService.getRevenueStats(null, null));

        // ======== 聊天记录 ========
        testOne(results, "聊天记录列表", "conversations", () ->
                adminService.getConversations(null, 0, 5));
        testOne(results, "聊天消息详情", "conversations", () -> {
            var convs = adminService.getConversations(null, 0, 1);
            if (convs.hasContent()) {
                adminService.getConversationMessages(convs.getContent().get(0).getId());
            }
        });

        // ======== 用户端接口 ========
        testOne(results, "会话列表", "user", () -> conversationService.getConversations(adminId));
        testOne(results, "计费余额", "user", () -> billingService.getUserBalance(adminId));
        testOne(results, "累计消费", "user", () -> billingService.getTotalSpent(adminId));
        testOne(results, "累计Token", "user", () -> billingService.getTotalTokens(adminId));
        testOne(results, "知识库列表", "user", () -> knowledgeBaseService.listByUser(adminId));
        testOne(results, "提示词社区浏览", "user", () -> promptsHubService.browse(null, "likes", 0, 5));

        return ResponseEntity.ok(results);
    }

    private void testOne(List<Map<String, Object>> results, String name, String category, Runnable test) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("category", category);

        Instant start = Instant.now();
        try {
            test.run();
            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            result.put("status", 200);
            result.put("timeMs", elapsedMs);
            result.put("success", true);
            result.put("detail", "OK");
        } catch (Exception e) {
            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            result.put("status", -1);
            result.put("timeMs", elapsedMs);
            result.put("success", false);
            String msg = e.getMessage();
            if (msg != null && msg.length() > 80) {
                result.put("detail", msg.substring(0, 80) + "...");
            } else {
                result.put("detail", msg);
            }
            logger.warn("API健康检查失败: {} -> {}", name, msg);
        }
        results.add(result);
    }
}
