package com.example.aichat.controller;

import com.example.aichat.model.FriendMessage;
import com.example.aichat.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Autowired
    private FriendService friendService;

    /** 搜索用户 */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam String keyword, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(friendService.searchUsers(keyword, userId));
    }

    /** 发送好友申请 */
    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestBody Map<String, Object> body, Authentication auth) {
        Long fromUserId = (Long) auth.getPrincipal();
        Long toUserId = Long.valueOf(body.get("userId").toString());
        try {
            friendService.sendFriendRequest(fromUserId, toUserId);
            return ResponseEntity.ok(Map.of("message", "申请已发送"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 接受好友申请 */
    @PostMapping("/accept")
    public ResponseEntity<?> acceptRequest(@RequestBody Map<String, Object> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long friendshipId = Long.valueOf(body.get("friendshipId").toString());
        try {
            friendService.acceptRequest(friendshipId, userId);
            return ResponseEntity.ok(Map.of("message", "已接受"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 拒绝好友申请 */
    @PostMapping("/reject")
    public ResponseEntity<?> rejectRequest(@RequestBody Map<String, Object> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long friendshipId = Long.valueOf(body.get("friendshipId").toString());
        try {
            friendService.rejectRequest(friendshipId, userId);
            return ResponseEntity.ok(Map.of("message", "已拒绝"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 好友列表 */
    @GetMapping("/list")
    public ResponseEntity<?> getFriendList(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(friendService.getFriendList(userId));
    }

    /** 待处理的好友申请 */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(friendService.getPendingRequests(userId));
    }

    /** 发送消息 */
    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> body, Authentication auth) {
        Long senderId = (Long) auth.getPrincipal();
        Long friendshipId = Long.valueOf(body.get("friendshipId").toString());
        String content = body.get("content").toString();
        if (content.isBlank()) return ResponseEntity.badRequest().build();
        try {
            FriendMessage msg = friendService.sendMessage(senderId, friendshipId, content);
            return ResponseEntity.ok(Map.of(
                    "id", msg.getId(),
                    "senderId", msg.getSenderId(),
                    "content", msg.getContent(),
                    "createdAt", msg.getCreatedAt()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 获取与某好友的聊天记录 */
    @GetMapping("/chat/{friendUserId}")
    public ResponseEntity<?> getChatHistory(@PathVariable Long friendUserId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(friendService.getChatHistory(userId, friendUserId));
    }

    /** 标记消息已读 */
    @PostMapping("/read/{senderId}")
    public ResponseEntity<?> markAsRead(@PathVariable Long senderId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        friendService.markAsRead(userId, senderId);
        return ResponseEntity.ok().build();
    }
}
