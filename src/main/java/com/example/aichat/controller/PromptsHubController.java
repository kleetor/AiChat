package com.example.aichat.controller;

import com.example.aichat.model.Comment;
import com.example.aichat.model.PromptsHub;
import com.example.aichat.model.User;
import com.example.aichat.repository.UserRepository;
import com.example.aichat.service.CommentService;
import com.example.aichat.service.PromptsHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/prompts-hub")
public class PromptsHubController {

    @Autowired
    private PromptsHubService promptsHubService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllPrompts() {
        List<PromptsHub> prompts = promptsHubService.getAllPrompts();
        // 批量加载用户头像
        Map<Long, String> avatarMap = new HashMap<>();
        for (PromptsHub p : prompts) {
            if (!avatarMap.containsKey(p.getUserId())) {
                avatarMap.put(p.getUserId(),
                        userRepository.findById(p.getUserId())
                                .map(u -> u.getAvatarUrl() != null ? u.getAvatarUrl() : "")
                                .orElse(""));
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (PromptsHub p : prompts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("content", p.getContent());
            item.put("userId", p.getUserId());
            item.put("userName", p.getUserName());
            item.put("userAvatar", avatarMap.getOrDefault(p.getUserId(), ""));
            item.put("userMessage", p.getUserMessage());
            item.put("likesCount", p.getLikesCount());
            item.put("imageUrl", p.getImageUrl());
            item.put("createdAt", p.getCreatedAt());
            item.put("commentCount", commentService.getCommentCount(p.getId()));
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPromptById(@PathVariable Long id) {
        PromptsHub p = promptsHubService.getPromptById(id);
        String avatar = userRepository.findById(p.getUserId())
                .map(u -> u.getAvatarUrl() != null ? u.getAvatarUrl() : "")
                .orElse("");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", p.getId());
        result.put("name", p.getName());
        result.put("content", p.getContent());
        result.put("userId", p.getUserId());
        result.put("userName", p.getUserName());
        result.put("userAvatar", avatar);
        result.put("userMessage", p.getUserMessage());
        result.put("likesCount", p.getLikesCount());
        result.put("imageUrl", p.getImageUrl());
        result.put("createdAt", p.getCreatedAt());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user")
    public ResponseEntity<List<PromptsHub>> getUserUploadedPrompts(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(promptsHubService.getUserUploadedPrompts(userId));
    }

    @PostMapping("/upload")
    public ResponseEntity<PromptsHub> uploadPrompt(@RequestBody Map<String, String> body,
                                                  Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.get("name");
        String content = body.get("content");
        String userMessage = body.get("userMessage");

        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        PromptsHub prompt = promptsHubService.uploadPrompt(userId, name, content, userMessage);
        return ResponseEntity.ok(prompt);
    }

    @PostMapping("/upload-with-image")
    public ResponseEntity<PromptsHub> uploadPromptWithImage(
            @RequestParam("name") String name,
            @RequestParam("content") String content,
            @RequestParam(value = "userMessage", required = false) String userMessage,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            PromptsHub prompt = promptsHubService.uploadPromptWithImage(userId, name, content, userMessage, image);
            return ResponseEntity.ok(prompt);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<Map<String, String>> uploadImageForPrompt(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image,
            Authentication auth) {
        try {
            PromptsHub prompt = promptsHubService.updateImageUrl(id, image);
            return ResponseEntity.ok(Map.of("imageUrl", prompt.getImageUrl()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<?> likePrompt(@PathVariable Long id,
                                         Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            promptsHubService.likePrompt(id, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }
    }

    // ======== 评论接口 ========

    /** 获取某提示词的评论列表 */
    @GetMapping("/{id}/comments")
    public ResponseEntity<List<Map<String, Object>>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(commentService.getCommentsWithReplies(id));
    }

    /** 获取某提示词的热门评论 */
    @GetMapping("/{id}/comments/hot")
    public ResponseEntity<List<Map<String, Object>>> getHotComments(@PathVariable Long id) {
        return ResponseEntity.ok(commentService.getHotComments(id, 3));
    }

    /** 发表评论 */
    @PostMapping("/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable Long id,
                                                           @RequestBody Map<String, String> body,
                                                           Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String parentIdStr = body.get("parentId");
        Long parentId = (parentIdStr != null && !parentIdStr.isBlank()) ? Long.parseLong(parentIdStr) : null;
        try {
            Comment comment = commentService.addComment(id, userId, content, parentId);
            String avatar = userRepository.findById(userId)
                    .map(u -> u.getAvatarUrl() != null ? u.getAvatarUrl() : "")
                    .orElse("");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", comment.getId());
            result.put("userName", comment.getUserName());
            result.put("userAvatar", avatar);
            result.put("content", comment.getContent());
            result.put("parentId", comment.getParentId());
            result.put("replyToName", comment.getReplyToName());
            result.put("likesCount", comment.getLikesCount());
            result.put("createdAt", comment.getCreatedAt());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 点赞评论 */
    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<?> likeComment(@PathVariable Long commentId,
                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            commentService.likeComment(commentId, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除评论 */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId,
                                            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            commentService.deleteComment(commentId, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
