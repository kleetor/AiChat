package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.PromptsHub;
import com.example.aichat.model.User;
import com.example.aichat.model.UserLike;
import com.example.aichat.model.UsageHistory;
import com.example.aichat.model.Favorite;
import com.example.aichat.model.PromptRating;
import com.example.aichat.repository.FavoriteRepository;
import com.example.aichat.repository.PromptRatingRepository;
import com.example.aichat.repository.PromptsHubRepository;
import com.example.aichat.repository.UsageHistoryRepository;
import com.example.aichat.repository.UserLikeRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PromptsHubService {

    private static final int DAILY_LIKE_LIMIT = 10;

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );

    @Autowired
    private PromptsHubRepository promptsHubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLikeRepository userLikeRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private UsageHistoryRepository usageHistoryRepository;

    @Autowired
    private PromptRatingRepository promptRatingRepository;

    @Value("${upload.dir:./uploads/images}")
    private String uploadDir;

    @Value("${upload.url-prefix:/uploads/images}")
    private String uploadUrlPrefix;

    public List<PromptsHub> getAllPrompts() {
        return promptsHubRepository.findAllByOrderByLikesCountDescCreatedAtDesc();
    }

    public PromptsHub getPromptById(Long id) {
        return promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
    }

    public PromptsHub uploadPrompt(Long userId, String name, String content, String userMessage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        PromptsHub prompt = PromptsHub.builder()
                .name(name)
                .content(content)
                .userId(userId)
                .userName(user.getUsername())
                .userMessage(userMessage)
                .build();

        return promptsHubRepository.save(prompt);
    }

    @Transactional
    public PromptsHub uploadPromptWithImage(Long userId, String name, String content,
                                             String userMessage, MultipartFile image) {
        PromptsHub prompt = uploadPrompt(userId, name, content, userMessage);
        if (image != null && !image.isEmpty()) {
            String imageUrl = saveImage(image);
            prompt.setImageUrl(imageUrl);
            promptsHubRepository.save(prompt);
        } else if (prompt.getImageUrl() == null) {
            prompt.setImageUrl(pickRandomCover());
            promptsHubRepository.save(prompt);
        }
        return prompt;
    }

    @Transactional
    public void likePrompt(Long id, Long userId) {
        if (!promptsHubRepository.existsById(id)) {
            throw BusinessException.notFound("提示词不存在");
        }
        // 重复点赞
        if (userLikeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, "PROMPT", id)) {
            throw BusinessException.conflict("已经点过赞了");
        }
        // 每日上限
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = userLikeRepository.countTodayByUserAndType(userId, "PROMPT", todayStart);
        if (todayCount >= DAILY_LIKE_LIMIT) {
            throw BusinessException.badRequest("每日点赞已达上限（" + DAILY_LIKE_LIMIT + "次）");
        }
        promptsHubRepository.incrementLikes(id);
        userLikeRepository.save(UserLike.builder()
                .userId(userId)
                .targetType("PROMPT")
                .targetId(id)
                .build());

        // 发送通知
        PromptsHub hub = promptsHubRepository.findById(id).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (hub != null && user != null) {
            notificationService.create(userId, user.getUsername(),
                    hub.getUserId(), "PROMPT_LIKE",
                    user.getUsername() + " 点赞了你的提示词",
                    hub.getName(),
                    id, null);
        }
    }

    public List<PromptsHub> getUserUploadedPrompts(Long userId) {
        return promptsHubRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ======== 社区浏览 ========

    /** 分页浏览（支持分类+排序） */
    public Page<PromptsHub> browse(String category, String sort, int page, int size) {
        return promptsHubRepository.findAll(
                PromptsHubSpecification.browse(category, sort),
                PageRequest.of(page, size));
    }

    /** 获取所有分类 */
    public List<String> getCategories() {
        return promptsHubRepository.findDistinctCategories();
    }

    /** 精选推荐 */
    public Page<PromptsHub> getFeatured(int page, int size) {
        return promptsHubRepository.findByFeaturedTrueOrderByCreatedAtDesc(
                PageRequest.of(page, size));
    }

    /** FULLTEXT 关键词搜索 */
    public Page<PromptsHub> search(String query, String category, int page, int size) {
        String escaped = escapeFulltext(query);
        if (category != null && !category.isBlank()) {
            return promptsHubRepository.searchFulltextByCategory(
                    escaped, category, PageRequest.of(page, size));
        }
        return promptsHubRepository.searchFulltext(escaped, PageRequest.of(page, size));
    }

    /** 转义 FULLTEXT 布尔模式特殊字符 */
    private String escapeFulltext(String query) {
        if (query == null || query.isBlank()) return "";
        // FULLTEXT 布尔模式特殊字符: + - > < ( ) ~ * " @
        return query.replaceAll("([+\\-><\\(\\)~\\*\"@])", "\\\\$1");
    }

    // ======== 收藏 ========

    @Transactional
    public void savePrompt(Long promptId, Long userId) {
        if (favoriteRepository.existsByUserIdAndPromptId(userId, promptId)) {
            return;
        }
        Favorite fav = Favorite.builder()
                .user(userRepository.getReferenceById(userId))
                .prompt(promptsHubRepository.getReferenceById(promptId))
                .build();
        favoriteRepository.save(fav);
        promptsHubRepository.incrementSaveCount(promptId);
        recordUsage(userId, promptId, "save");
    }

    @Transactional
    public void unsavePrompt(Long promptId, Long userId) {
        favoriteRepository.deleteByUserIdAndPromptId(userId, promptId);
        promptsHubRepository.decrementSaveCount(promptId);
    }

    public boolean isSaved(Long promptId, Long userId) {
        return favoriteRepository.existsByUserIdAndPromptId(userId, promptId);
    }

    @Transactional
    public void incrementViewCount(Long promptId) {
        promptsHubRepository.incrementViewCount(promptId);
    }

    // ======== 评分 ========

    @Transactional
    public void ratePrompt(Long promptId, Long userId, int rating) {
        if (rating < 1 || rating > 5) {
            throw BusinessException.badRequest("评分范围为 1-5");
        }
        PromptRating pr = promptRatingRepository.findByUserIdAndPromptId(userId, promptId)
                .orElseGet(() -> PromptRating.builder()
                        .userId(userId)
                        .promptId(promptId)
                        .build());
        pr.setRating(rating);
        promptRatingRepository.save(pr);
        // 异步重算 avg_rating
        recalcAvgRating(promptId);
        recordUsage(userId, promptId, "rate");
    }

    public Optional<PromptRating> getUserRating(Long promptId, Long userId) {
        return promptRatingRepository.findByUserIdAndPromptId(userId, promptId);
    }

    private void recalcAvgRating(Long promptId) {
        double avg = promptRatingRepository.calcAvgRating(promptId);
        PromptsHub p = promptsHubRepository.findById(promptId).orElse(null);
        if (p != null) {
            p.setAvgRating(java.math.BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP));
            promptsHubRepository.save(p);
        }
    }

    // ======== 编辑器 ========

    @Transactional
    public PromptsHub createPrompt(Long userId, String name, String content,
                                    String description, String category, String tags,
                                    String modelSupport, String userMessage, boolean publish) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        PromptsHub prompt = PromptsHub.builder()
                .name(name)
                .content(content)
                .userId(userId)
                .userName(user.getUsername())
                .userMessage(userMessage)
                .description(description)
                .category(category)
                .tags(tags)
                .modelSupport(modelSupport)
                .status(publish ? "pending_review" : "draft")
                .imageUrl(pickRandomCover())
                .build();
        return promptsHubRepository.save(prompt);
    }

    @Transactional
    public PromptsHub updatePrompt(Long id, Long userId, String name, String content,
                                    String description, String category, String tags,
                                    String modelSupport, String userMessage, boolean publish) {
        PromptsHub p = promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (!p.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权修改");
        }
        if (name != null) p.setName(name);
        if (content != null) p.setContent(content);
        if (description != null) p.setDescription(description);
        if (category != null) p.setCategory(category);
        if (tags != null) p.setTags(tags);
        if (modelSupport != null) p.setModelSupport(modelSupport);
        if (userMessage != null) p.setUserMessage(userMessage);
        p.setStatus(publish ? "pending_review" : "draft");
        p.setUpdatedAt(LocalDateTime.now());
        return promptsHubRepository.save(p);
    }

    // ======== 我的创作 ========

    public Page<PromptsHub> getUserPrompts(Long userId, String status, int page, int size) {
        if (status != null && !status.isBlank()) {
            return promptsHubRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    userId, status, PageRequest.of(page, size));
        }
        return promptsHubRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    @Transactional
    public void removePrompt(Long id, Long userId) {
        int updated = promptsHubRepository.removeByUser(id, userId);
        if (updated == 0) {
            throw BusinessException.forbidden("无权操作或提示词不存在");
        }
    }

    @Transactional
    public void deletePrompt(Long id, Long userId) {
        PromptsHub p = promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (!p.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权操作");
        }
        if ("published".equals(p.getStatus()) || "pending_review".equals(p.getStatus())) {
            throw BusinessException.badRequest("已发布或审核中的提示词无法删除，请先下架");
        }
        promptsHubRepository.delete(p);
    }

    // ======== 使用历史 ========

    public Page<UsageHistory> getUsageHistory(Long userId, int page, int size) {
        return usageHistoryRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    public void clearUsageHistory(Long userId) {
        usageHistoryRepository.deleteByUserId(userId);
    }

    @Transactional
    public void recordUsage(Long userId, Long promptId, String action) {
        UsageHistory history = UsageHistory.builder()
                .user(userRepository.getReferenceById(userId))
                .prompt(promptsHubRepository.getReferenceById(promptId))
                .action(action)
                .build();
        usageHistoryRepository.save(history);
    }

    public PromptsHub updateImageUrl(Long id, MultipartFile image) {
        PromptsHub prompt = promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (image != null && !image.isEmpty()) {
            String imageUrl = saveImage(image);
            prompt.setImageUrl(imageUrl);
            promptsHubRepository.save(prompt);
        }
        return prompt;
    }

    private String saveImage(MultipartFile image) {
        if (image.getContentType() == null || !ALLOWED_IMAGE_TYPES.contains(image.getContentType().toLowerCase())) {
            throw new IllegalArgumentException("仅支持 PNG / JPG / GIF / WEBP 图片");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }
        String resolvedDir = uploadDir;
        if (!new File(uploadDir).isAbsolute()) {
            resolvedDir = System.getProperty("user.dir") + File.separator + uploadDir;
        }
        File dir = new File(resolvedDir);
        try {
            resolvedDir = dir.getCanonicalPath();
            dir = new File(resolvedDir);
        } catch (java.io.IOException ignored) {
            // fallback to absolute path
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("无法创建上传目录: " + dir.getAbsolutePath());
        }

        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID() + extension.toLowerCase();
        File target = new File(dir, newFilename);

        try {
            image.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("图片保存失败: " + e.getMessage(), e);
        }

        String prefix = uploadUrlPrefix.endsWith("/") ? uploadUrlPrefix : uploadUrlPrefix + "/";
        return prefix + newFilename;
    }

    /** 从 random-covers 目录随机选取一张封面图，返回 URL 路径 */
    private String pickRandomCover() {
        String dir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "random-covers";
        File folder = new File(dir);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp");
            });
            if (files != null && files.length > 0) {
                int idx = ThreadLocalRandom.current().nextInt(files.length);
                return "/uploads/random-covers/" + files[idx].getName();
            }
        }
        return null;
    }
}
