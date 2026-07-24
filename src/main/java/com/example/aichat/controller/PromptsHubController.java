package com.example.aichat.controller;

import com.example.aichat.model.Comment;
import com.example.aichat.model.PromptsHub;
import com.example.aichat.model.UsageHistory;
import com.example.aichat.model.User;
import com.example.aichat.repository.UserRepository;
import com.example.aichat.service.CommentService;
import com.example.aichat.service.PromptsHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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

    // ======== 社区浏览 ========

    /** 分页浏览（支持分类+排序） */
    @GetMapping
    public ResponseEntity<Page<Map<String, Object>>> browse(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "likes") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PromptsHub> prompts = promptsHubService.browse(category, sort, page, size);
        Map<Long, String> avatarMap = loadAvatarMap(prompts);
        Page<Map<String, Object>> result = prompts.map(p -> toSummaryMap(p, avatarMap));
        return ResponseEntity.ok(result);
    }

    /** 分类列表 */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(promptsHubService.getCategories());
    }

    /** 精选推荐 */
    @GetMapping("/featured")
    public ResponseEntity<Page<Map<String, Object>>> getFeatured(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PromptsHub> prompts = promptsHubService.getFeatured(page, size);
        Map<Long, String> avatarMap = loadAvatarMap(prompts);
        return ResponseEntity.ok(prompts.map(p -> toSummaryMap(p, avatarMap)));
    }

    /** FULLTEXT 关键词搜索 */
    @GetMapping("/search")
    public ResponseEntity<Page<Map<String, Object>>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (q.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (q.length() > 200) {
            return ResponseEntity.badRequest().build();
        }
        Page<PromptsHub> prompts = promptsHubService.search(q, category, page, size);
        Map<Long, String> avatarMap = loadAvatarMap(prompts);
        return ResponseEntity.ok(prompts.map(p -> toSummaryMap(p, avatarMap)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPromptById(@PathVariable Long id, Authentication auth) {
        PromptsHub p = promptsHubService.getPromptById(id);
        // 浏览量 +1
        promptsHubService.incrementViewCount(id);
        Map<Long, String> avatarMap = loadAvatarMap(p);
        Map<String, Object> result = toDetailMap(p, avatarMap);
        // 收藏状态（仅登录用户）
        if (auth != null) {
            Long userId = (Long) auth.getPrincipal();
            result.put("isSaved", promptsHubService.isSaved(id, userId));
        } else {
            result.put("isSaved", false);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user")
    public ResponseEntity<List<PromptsHub>> getUserUploadedPrompts(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(promptsHubService.getUserUploadedPrompts(userId));
    }

    // ======== 我的创作 ========

    @GetMapping("/my")
    public ResponseEntity<Page<Map<String, Object>>> getMyPrompts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Page<PromptsHub> prompts = promptsHubService.getUserPrompts(userId, status, page, size);
        Map<Long, String> avatarMap = loadAvatarMap(prompts);
        return ResponseEntity.ok(prompts.map(p -> toSummaryMap(p, avatarMap)));
    }

    /** 下架 */
    @PostMapping("/{id}/remove")
    public ResponseEntity<?> removePrompt(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        promptsHubService.removePrompt(id, userId);
        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    /** 删除（仅草稿和已下架可删除） */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePrompt(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            promptsHubService.deletePrompt(id, userId);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
        if (content.length() > 10000) {
            return ResponseEntity.badRequest().build();
        }

        PromptsHub prompt = promptsHubService.uploadPrompt(userId, name, content, userMessage);
        return ResponseEntity.ok(prompt);
    }

    // ======== 编辑器 ========

    /** 创建/保存提示词（支持草稿和发布） */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createPrompt(@RequestBody Map<String, String> body,
                                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.get("name");
        String content = body.get("content");
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name 和 content 不能为空"));
        }
        boolean publish = "true".equals(body.get("publish"));
        String desc = body.get("description");
        String cat = body.get("category");
        String tags = body.get("tags");
        if (desc != null && desc.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "描述长度不能超过500字符"));
        }
        if (tags != null && tags.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "标签长度不能超过500字符"));
        }
        PromptsHub p = promptsHubService.createPrompt(
                userId, name, content,
                desc, cat, tags,
                body.get("modelSupport"),
                body.get("userMessage"),
                publish);
        return ResponseEntity.ok(toDetailMap(p, loadAvatarMap(p)));
    }

    /** 编辑提示词 */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePrompt(@PathVariable Long id,
                                                             @RequestBody Map<String, String> body,
                                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean publish = "true".equals(body.get("publish"));
        String desc = body.get("description");
        String cat = body.get("category");
        String tags = body.get("tags");
        if (desc != null && desc.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "描述长度不能超过500字符"));
        }
        if (tags != null && tags.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "标签长度不能超过500字符"));
        }
        PromptsHub p = promptsHubService.updatePrompt(
                id, userId,
                body.get("name"),
                body.get("content"),
                desc, cat, tags,
                body.get("modelSupport"),
                body.get("userMessage"),
                publish);
        return ResponseEntity.ok(toDetailMap(p, loadAvatarMap(p)));
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
        if (image != null && !image.isEmpty()) {
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().build();
            }
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
        if (image != null && !image.isEmpty()) {
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("error", "只支持图片文件"));
            }
        }
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

    // ======== 收藏 ========

    @PostMapping("/{id}/save")
    public ResponseEntity<?> savePrompt(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        promptsHubService.savePrompt(id, userId);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @PostMapping("/{id}/unsave")
    public ResponseEntity<?> unsavePrompt(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        promptsHubService.unsavePrompt(id, userId);
        return ResponseEntity.ok(Map.of("saved", false));
    }

    @GetMapping("/{id}/saved")
    public ResponseEntity<Map<String, Boolean>> isSaved(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(Map.of("saved", promptsHubService.isSaved(id, userId)));
    }

    // ======== 评分 ========

    @PostMapping("/{id}/rate")
    public ResponseEntity<?> ratePrompt(@PathVariable Long id,
                                         @RequestBody Map<String, Integer> body,
                                         Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Integer rating = body.get("rating");
        if (rating == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "rating 不能为空"));
        }
        try {
            promptsHubService.ratePrompt(id, userId, rating);
            return ResponseEntity.ok(Map.of("rating", rating));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ======== 使用历史 ========

    @GetMapping("/usage-history")
    public ResponseEntity<Page<Map<String, Object>>> getUsageHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Page<UsageHistory> history = promptsHubService.getUsageHistory(userId, page, size);
        return ResponseEntity.ok(history.map(h -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", h.getId());
            item.put("promptId", h.getPrompt().getId());
            item.put("promptName", h.getPrompt().getName());
            item.put("action", h.getAction());
            item.put("createdAt", h.getCreatedAt());
            return item;
        }));
    }

    @DeleteMapping("/usage-history")
    public ResponseEntity<?> clearUsageHistory(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        promptsHubService.clearUsageHistory(userId);
        return ResponseEntity.ok(Map.of("message", "已清空"));
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

    // ======== 随机封面图 ========

    /** 获取 random-covers 目录下所有封面图文件名列表 */
    @GetMapping("/random-covers")
    public ResponseEntity<List<String>> getRandomCovers() {
        String baseDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "random-covers";
        File dir = new File(baseDir);
        List<String> filenames = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp");
            });
            if (files != null) {
                for (File f : files) {
                    filenames.add("/uploads/random-covers/" + f.getName());
                }
            }
        }
        return ResponseEntity.ok(filenames);
    }

    /** 从 random-covers 目录随机返回一张封面图 URL */
    @GetMapping("/random-cover")
    public ResponseEntity<Map<String, String>> getRandomCover() {
        String baseDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "random-covers";
        File dir = new File(baseDir);
        Map<String, String> result = new LinkedHashMap<>();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp");
            });
            if (files != null && files.length > 0) {
                int randomIndex = new Random().nextInt(files.length);
                String filename = files[randomIndex].getName();
                result.put("imageUrl", "/uploads/random-covers/" + filename);
                return ResponseEntity.ok(result);
            }
        }
        result.put("imageUrl", "");
        return ResponseEntity.ok(result);
    }

    // ======== 辅助方法 ========

    /** 批量加载用户头像 */
    private Map<Long, String> loadAvatarMap(Page<PromptsHub> prompts) {
        Set<Long> userIds = prompts.getContent().stream()
                .map(PromptsHub::getUserId).collect(Collectors.toSet());
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getAvatarUrl() != null ? u.getAvatarUrl() : ""));
    }

    /** 加载单个提示词的用户头像 */
    private Map<Long, String> loadAvatarMap(PromptsHub p) {
        return userRepository.findById(p.getUserId())
                .map(u -> Map.of(p.getUserId(), u.getAvatarUrl() != null ? u.getAvatarUrl() : ""))
                .orElse(Map.of(p.getUserId(), ""));
    }

    /** 列表摘要（不含 content 全文） */
    private Map<String, Object> toSummaryMap(PromptsHub p, Map<Long, String> avatarMap) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", p.getId());
        item.put("name", p.getName());
        item.put("content", p.getContent());
        item.put("description", p.getDescription());
        item.put("category", p.getCategory());
        item.put("tags", p.getTags());
        item.put("userId", p.getUserId());
        item.put("userName", p.getUserName());
        item.put("userAvatar", avatarMap.getOrDefault(p.getUserId(), ""));
        item.put("likesCount", p.getLikesCount());
        item.put("saveCount", p.getSaveCount());
        item.put("viewCount", p.getViewCount());
        item.put("avgRating", p.getAvgRating());
        item.put("imageUrl", p.getImageUrl());
        item.put("featured", p.getFeatured());
        item.put("status", p.getStatus());
        item.put("createdAt", p.getCreatedAt());
        item.put("commentCount", commentService.getCommentCount(p.getId()));
        return item;
    }

    /** 详情页（含 content 全文 + 统计） */
    private Map<String, Object> toDetailMap(PromptsHub p, Map<Long, String> avatarMap) {
        Map<String, Object> item = toSummaryMap(p, avatarMap);
        item.put("content", p.getContent());
        item.put("userMessage", p.getUserMessage());
        item.put("modelSupport", p.getModelSupport());
        item.put("status", p.getStatus());
        item.put("version", p.getVersion());
        item.put("updatedAt", p.getUpdatedAt());
        return item;
    }
}
