// controller/ChatController.java（完整）
package com.example.aichat.controller;

import com.example.aichat.dto.ChatHistoryResponse;
import com.example.aichat.dto.ChatRequest;
import com.example.aichat.dto.ChatResponse;
import com.example.aichat.model.Conversation;
import com.example.aichat.service.ChatHistoryService;
import com.example.aichat.service.ChatService;
import com.example.aichat.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ConversationService conversationService;

    // 聊天：接收消息和会话ID（必须）
    @PostMapping("/chat/{conversationId}")
    public ResponseEntity<ChatResponse> chat(@PathVariable Long conversationId,
                                             @RequestBody ChatRequest request,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (!conversationService.belongsToUser(conversationId, userId)) {
            return ResponseEntity.status(403).build();
        }
        // 传入 promptId
        String reply = chatService.chatAndSave(conversationId, request.getMessage(), request.getPromptId());
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        return ResponseEntity.ok(response);
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
}
