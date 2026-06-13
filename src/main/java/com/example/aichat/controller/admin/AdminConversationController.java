package com.example.aichat.controller.admin;

import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminConversationController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/conversations")
    public ResponseEntity<Page<Conversation>> getConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(adminService.getConversations(userId, page, size));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(
            @PathVariable Long id) {
        return ResponseEntity.ok(adminService.getConversationMessages(id));
    }
}
