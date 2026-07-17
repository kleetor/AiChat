package com.example.aichat.controller;

import com.example.aichat.model.Notification;
import com.example.aichat.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /** 获取通知列表 */
    @GetMapping("/notifications")
    public ResponseEntity<List<Map<String, Object>>> getNotifications(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<Notification> list = notificationService.getNotifications(userId);
        List<Map<String, Object>> result = list.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("promptId", n.getPromptId());
            m.put("commentId", n.getCommentId());
            m.put("fromUserName", n.getFromUserName());
            m.put("isRead", n.getIsRead());
            m.put("createdAt", n.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** 未读数 */
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** 全部标记已读 */
    @PostMapping("/notifications/read-all")
    public ResponseEntity<?> markAllAsRead(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    /** 单条标记已读 */
    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            notificationService.markAsRead(id, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除单条通知 */
    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            notificationService.delete(id, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
