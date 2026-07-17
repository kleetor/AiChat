package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.Comment;
import com.example.aichat.model.User;
import com.example.aichat.model.UserLike;
import com.example.aichat.repository.CommentRepository;
import com.example.aichat.repository.PromptsHubRepository;
import com.example.aichat.repository.UserLikeRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CommentService {

    private static final int DAILY_LIKE_LIMIT = 10;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLikeRepository userLikeRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PromptsHubRepository promptsHubRepository;

    /** 发表顶层评论或回复 */
    @Transactional
    public Comment addComment(Long promptId, Long userId, String content, Long parentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        Comment comment = Comment.builder()
                .promptId(promptId)
                .userId(userId)
                .userName(user.getUsername())
                .content(content)
                .parentId(parentId)
                .build();
        // 如果是回复，查父评论作者名
        if (parentId != null) {
            commentRepository.findById(parentId).ifPresent(parent -> {
                comment.setReplyToName(parent.getUserName());
            });
        }
        Comment saved = commentRepository.save(comment);

        // 发送通知
        if (parentId == null) {
            // 顶层评论：通知提示词作者
            promptsHubRepository.findById(promptId).ifPresent(hub -> {
                notificationService.create(userId, user.getUsername(),
                        hub.getUserId(), "PROMPT_COMMENT",
                        user.getUsername() + " 评论了你的提示词",
                        content.length() > 50 ? content.substring(0, 50) + "..." : content,
                        promptId, saved.getId());
            });
        } else {
            // 回复：通知父评论作者
            commentRepository.findById(parentId).ifPresent(parent -> {
                notificationService.create(userId, user.getUsername(),
                        parent.getUserId(), "COMMENT_REPLY",
                        user.getUsername() + " 回复了你的评论",
                        content.length() > 50 ? content.substring(0, 50) + "..." : content,
                        promptId, saved.getId());
            });
        }

        return saved;
    }

    /** 获取某提示词的评论树（顶层+所有层级回复扁平化） */
    public List<Map<String, Object>> getCommentsWithReplies(Long promptId) {
        List<Comment> all = commentRepository.findByPromptIdOrderByCreatedAtAsc(promptId);
        if (all.isEmpty()) return Collections.emptyList();

        // 批量加载所有评论者的头像
        Map<Long, String> avatarMap = loadAvatarMap(all);

        // 构建 id -> comment 映射
        Map<Long, Comment> idMap = new LinkedHashMap<>();
        for (Comment c : all) {
            idMap.put(c.getId(), c);
        }

        // 分离顶层评论
        List<Comment> topComments = new ArrayList<>();
        for (Comment c : all) {
            if (c.getParentId() == null) {
                topComments.add(c);
            }
        }

        // 为每个顶层评论收集所有后代（递归向上找根）
        Map<Long, List<Comment>> rootToDescendants = new LinkedHashMap<>();
        for (Comment top : topComments) {
            rootToDescendants.put(top.getId(), new ArrayList<>());
        }

        for (Comment c : all) {
            if (c.getParentId() != null) {
                // 向上走找到根评论
                Long rootId = findRootId(c.getParentId(), idMap);
                if (rootId != null && rootToDescendants.containsKey(rootId)) {
                    rootToDescendants.get(rootId).add(c);
                }
            }
        }

        // 构建结果（所有后代一律扁平）
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment top : topComments) {
            Map<String, Object> item = toMap(top, avatarMap);
            item.put("replies",
                    rootToDescendants.get(top.getId()).stream()
                            .map(c -> toMap(c, avatarMap))
                            .toList());
            result.add(item);
        }
        return result;
    }

    /** 向上递归找根评论ID */
    private Long findRootId(Long commentId, Map<Long, Comment> idMap) {
        Set<Long> visited = new HashSet<>();
        Long current = commentId;
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            Comment c = idMap.get(current);
            if (c == null) return null;
            if (c.getParentId() == null) return c.getId();
            current = c.getParentId();
        }
        return null;
    }

    /** 获取热门评论（前3条） */
    public List<Map<String, Object>> getHotComments(Long promptId, int limit) {
        List<Comment> hotList = commentRepository.findHotComments(promptId);
        Map<Long, String> avatarMap = loadAvatarMap(hotList);
        return hotList.stream()
                .limit(limit)
                .map(c -> toMap(c, avatarMap))
                .toList();
    }

    /** 点赞评论（含每日上限 + 重复校验） */
    @Transactional
    public void likeComment(Long commentId, Long userId) {
        if (!commentRepository.existsById(commentId)) {
            throw BusinessException.notFound("评论不存在");
        }
        // 重复点赞
        if (userLikeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, "COMMENT", commentId)) {
            throw BusinessException.conflict("已经点过赞了");
        }
        // 每日上限
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = userLikeRepository.countTodayByUserAndType(userId, "COMMENT", todayStart);
        if (todayCount >= DAILY_LIKE_LIMIT) {
            throw BusinessException.badRequest("今日评论点赞已达上限（" + DAILY_LIKE_LIMIT + "次）");
        }
        commentRepository.incrementLikes(commentId);
        userLikeRepository.save(UserLike.builder()
                .userId(userId)
                .targetType("COMMENT")
                .targetId(commentId)
                .build());

        // 发送通知
        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                notificationService.create(userId, user.getUsername(),
                        comment.getUserId(), "COMMENT_LIKE",
                        user.getUsername() + " 点赞了你的评论",
                        comment.getContent().length() > 50
                                ? comment.getContent().substring(0, 50) + "..."
                                : comment.getContent(),
                        comment.getPromptId(), commentId);
            }
        }
    }

    /** 
     * 删除评论：
     * - 提示词卡片主人可删除评论区任意评论（级联删所有后代）
     * - 非主人只能删自己的评论，且其子评论中有他人评论时不可删除
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> BusinessException.notFound("评论不存在"));
        Long promptId = comment.getPromptId();

        // 判断是否为提示词卡片主人
        boolean isOwner = promptsHubRepository.findById(promptId)
                .map(hub -> hub.getUserId().equals(userId))
                .orElse(false);

        if (isOwner) {
            // 主人可删除任意评论，级联所有后代
            List<Comment> allDescendants = new ArrayList<>();
            collectDescendants(commentId, allDescendants);
            commentRepository.deleteAll(allDescendants);
            commentRepository.delete(comment);
            return;
        }

        // 非主人只能删自己的评论
        if (!comment.getUserId().equals(userId)) {
            throw BusinessException.forbidden("你无法删除别人的评论");
        }

        // 检查直接子评论中是否有其他用户的评论
        List<Comment> children = commentRepository.findByParentIdOrderByCreatedAtAsc(commentId);
        boolean hasOthersReply = children.stream()
                .anyMatch(child -> !child.getUserId().equals(userId));
        if (hasOthersReply) {
            throw BusinessException.forbidden("你无法删除别人的评论");
        }

        // 子评论全是自己的，递归级联删除
        List<Comment> allDescendants = new ArrayList<>();
        collectDescendants(commentId, allDescendants);
        commentRepository.deleteAll(allDescendants);
        commentRepository.delete(comment);
    }

    /** 递归收集某条评论的所有后代 */
    private void collectDescendants(Long parentId, List<Comment> result) {
        List<Comment> children = commentRepository.findByParentIdOrderByCreatedAtAsc(parentId);
        for (Comment child : children) {
            result.add(child);
            collectDescendants(child.getId(), result);
        }
    }

    /** 获取某提示词的评论总数 */
    public long getCommentCount(Long promptId) {
        return commentRepository.countByPromptId(promptId);
    }

    /** 批量加载评论者的头像 */
    private Map<Long, String> loadAvatarMap(List<Comment> comments) {
        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        return userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u.getAvatarUrl() != null ? u.getAvatarUrl() : ""));
    }

    private Map<String, Object> toMap(Comment c, Map<Long, String> avatarMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("promptId", c.getPromptId());
        map.put("userId", c.getUserId());
        map.put("userName", c.getUserName());
        map.put("userAvatar", avatarMap.getOrDefault(c.getUserId(), ""));
        map.put("content", c.getContent());
        map.put("parentId", c.getParentId());
        map.put("replyToName", c.getReplyToName());
        map.put("likesCount", c.getLikesCount());
        map.put("createdAt", c.getCreatedAt());
        return map;
    }
}
