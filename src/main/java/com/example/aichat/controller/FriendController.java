package com.example.aichat.controller;

import com.example.aichat.dto.FriendRequestDTO;
import com.example.aichat.model.FriendMessage;
import com.example.aichat.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> sendRequest(@Valid @RequestBody FriendRequestDTO body, Authentication auth) {
        Long fromUserId = (Long) auth.getPrincipal();
        friendService.sendFriendRequest(fromUserId, body.getUserId());
        return ResponseEntity.ok(Map.of("message", "申请已发送"));
    }

    /** 接受好友申请 */
    @PostMapping("/accept")
    public ResponseEntity<?> acceptRequest(@Valid @RequestBody FriendRequestDTO body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        friendService.acceptRequest(body.getFriendshipId(), userId);
        return ResponseEntity.ok(Map.of("message", "已接受"));
    }

    /** 拒绝好友申请 */
    @PostMapping("/reject")
    public ResponseEntity<?> rejectRequest(@Valid @RequestBody FriendRequestDTO body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        friendService.rejectRequest(body.getFriendshipId(), userId);
        return ResponseEntity.ok(Map.of("message", "已拒绝"));
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
    public ResponseEntity<?> sendMessage(@Valid @RequestBody FriendRequestDTO body, Authentication auth) {
        Long senderId = (Long) auth.getPrincipal();
        if (body.getContent() == null || body.getContent().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        FriendMessage msg = friendService.sendMessage(senderId, body.getFriendshipId(), body.getContent());
        return ResponseEntity.ok(Map.of(
                "id", msg.getId(),
                "senderId", msg.getSenderId(),
                "content", msg.getContent(),
                "createdAt", msg.getCreatedAt()
        ));
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
