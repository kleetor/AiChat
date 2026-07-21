package com.example.aichat.controller;

import com.example.aichat.dto.ChatHistoryResponse;
import com.example.aichat.dto.ChatRequest;
import com.example.aichat.dto.ChatResponse;
import com.example.aichat.model.Conversation;
import com.example.aichat.service.BillingService;
import com.example.aichat.service.ChatHistoryService;
import com.example.aichat.service.ChatService;
import com.example.aichat.service.LLMService;
import com.example.aichat.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private BillingService billingService;

    // 聊天：接收消息和会话ID（必须）
    @PostMapping("/chat/{conversationId}")
    public ResponseEntity<?> chat(@PathVariable Long conversationId,
                                  @Valid @RequestBody ChatRequest request,
                                  Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (!conversationService.belongsToUser(conversationId, userId)) {
            return ResponseEntity.status(403).build();
        }

        try {
            Long estimatedInputTokens = (long) (request.getMessage().length() * 1.3);
            billingService.checkAndReserveBalance(userId, request.getModelConfigId(), estimatedInputTokens);
        } catch (BillingService.InsufficientBalanceException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(402).body(error);
        }

        boolean webSearchEnabled = Boolean.TRUE.equals(request.getWebSearchEnabled());
        LLMService.TokenUsageResult result = chatService.chatAndSave(conversationId, request.getMessage(),
                request.getPromptId(), request.getModelConfigId(), webSearchEnabled, userId,
                request.getImageDescription(), request.getKnowledgeBaseId(),
                request.getLongMemoryEnabled(), request.getImageUrl(), request.getFileUrl());
        ChatResponse response = new ChatResponse();
        response.setReply(result.getReply());
        response.setInputTokens(result.getInputTokens());
        response.setOutputTokens(result.getOutputTokens());
        response.setCostAmount(result.getCostAmount());
        return ResponseEntity.ok(response);
    }

    // 流式聊天（SSE）
    @PostMapping(value = "/chat/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> chatStream(@PathVariable Long conversationId,
                                        @Valid @RequestBody ChatRequest request,
                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (!conversationService.belongsToUser(conversationId, userId)) {
            return ResponseEntity.status(403).build();
        }

        try {
            Long estimatedInputTokens = (long) (request.getMessage().length() * 1.3);
            billingService.checkAndReserveBalance(userId, request.getModelConfigId(), estimatedInputTokens);
        } catch (BillingService.InsufficientBalanceException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(402).body(error);
        }

        boolean webSearchEnabled = Boolean.TRUE.equals(request.getWebSearchEnabled());
        SseEmitter emitter = chatService.chatStream(
                conversationId,
                request.getMessage(),
                request.getPromptId(),
                request.getModelConfigId(),
                webSearchEnabled,
                userId,
                request.getImageDescription(),
                request.getKnowledgeBaseId(),
                request.getLongMemoryEnabled(),
                request.getImageUrl(),
                request.getFileUrl()
        );

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    // 获取某会话的历史
    @GetMapping("/chat/{conversationId}/history")
    public ResponseEntity<ChatHistoryResponse> getHistory(@PathVariable Long conversationId,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (!conversationService.belongsToUser(conversationId, userId)) {
            return ResponseEntity.status(403).body(null);
        }
        ChatHistoryResponse history = chatHistoryService.getChatHistoryByConversation(conversationId);
        return ResponseEntity.ok(history);
    }

    // 会话管理
    @PostMapping("/conversations")
    public ResponseEntity<Conversation> createConversation(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Conversation conv = conversationService.createConversation(userId);
        return ResponseEntity.ok(conv);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Conversation>> getConversations(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<Conversation> convs = conversationService.getConversations(userId);
        return ResponseEntity.ok(convs);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable Long id,
                                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (!conversationService.belongsToUser(id, userId)) {
            return ResponseEntity.status(403).body("无权限");
        }
        conversationService.deleteConversation(id);
        return ResponseEntity.ok().build();
    }

    // 删除单条消息
    @DeleteMapping("/chat/messages/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        try {
            chatHistoryService.deleteMessage(id, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
}
