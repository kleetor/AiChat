package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.FriendMessage;
import com.example.aichat.model.Friendship;
import com.example.aichat.model.User;
import com.example.aichat.repository.FriendMessageRepository;
import com.example.aichat.repository.FriendshipRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FriendService {

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private FriendMessageRepository friendMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    /** 搜索用户：通过PID或用户名模糊搜索 */
    public List<Map<String, Object>> searchUsers(String keyword, Long currentUserId) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // 1. 先尝试PID精确搜索
        Optional<User> pidUser = userRepository.findByPid(keyword);
        if (pidUser.isPresent()) {
            User u = pidUser.get();
            if (!u.getId().equals(currentUserId)) {
                seen.add(u.getId());
                result.add(toUserMap(u, currentUserId));
            }
        }

        // 2. 模糊搜索（用户名/邮箱/PID）
        Page<User> page = userRepository.searchByKeyword(keyword, Pageable.ofSize(20));
        for (User u : page.getContent()) {
            if (u.getId().equals(currentUserId)) continue;
            if (!seen.add(u.getId())) continue;
            result.add(toUserMap(u, currentUserId));
        }

        return result;
    }

    private Map<String, Object> toUserMap(User u, Long currentUserId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("pid", u.getPid());
        m.put("avatarUrl", u.getAvatarUrl() != null ? u.getAvatarUrl() : "");
        m.put("alreadyRelated", friendshipRepository.existsActiveRelation(currentUserId, u.getId()));
        return m;
    }

    /** 发送好友申请 */
    @Transactional
    public void sendFriendRequest(Long fromUserId, Long toUserId) {
        if (fromUserId.equals(toUserId)) {
            throw BusinessException.badRequest("不能添加自己为好友");
        }
        // 检查是否已存在
        if (friendshipRepository.existsActiveRelation(fromUserId, toUserId)) {
            throw BusinessException.conflict("已经是好友或已发送过申请");
        }
        Friendship fs = Friendship.builder()
                .userId(fromUserId)  // 发起方
                .friendId(toUserId)  // 接收方
                .status("PENDING")
                .build();
        friendshipRepository.save(fs);

        // 发送通知
        User fromUser = userRepository.findById(fromUserId).orElse(null);
        if (fromUser != null) {
            notificationService.create(fromUserId, fromUser.getUsername(),
                    toUserId, "FRIEND_REQUEST",
                    "好友申请",
                    fromUser.getUsername() + " 申请成为你的好友",
                    null, null);
        }
    }

    /** 接受好友申请 */
    @Transactional
    public void acceptRequest(Long friendshipId, Long currentUserId) {
        Friendship fs = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> BusinessException.notFound("申请不存在"));
        if (!fs.getFriendId().equals(currentUserId)) {
            throw BusinessException.forbidden("无权操作");
        }
        fs.setStatus("ACCEPTED");
        friendshipRepository.save(fs);

        // 发送通知
        User user = userRepository.findById(currentUserId).orElse(null);
        if (user != null) {
            notificationService.create(currentUserId, user.getUsername(),
                    fs.getUserId(), "FRIEND_ACCEPT",
                    "好友申请已通过",
                    user.getUsername() + " 已接受你的好友申请",
                    null, null);
        }
    }

    /** 拒绝好友申请 */
    @Transactional
    public void rejectRequest(Long friendshipId, Long currentUserId) {
        Friendship fs = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> BusinessException.notFound("申请不存在"));
        if (!fs.getFriendId().equals(currentUserId)) {
            throw BusinessException.forbidden("无权操作");
        }
        fs.setStatus("REJECTED");
        friendshipRepository.save(fs);
    }

    /** 获取好友列表（含用户信息） */
    public List<Map<String, Object>> getFriendList(Long userId) {
        List<Friendship> list = friendshipRepository.findAcceptedByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friendship fs : list) {
            Long friendId = fs.getUserId().equals(userId) ? fs.getFriendId() : fs.getUserId();
            User friend = userRepository.findById(friendId).orElse(null);
            if (friend == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("friendshipId", fs.getId());
            m.put("userId", friend.getId());
            m.put("username", friend.getUsername());
            m.put("pid", friend.getPid());
            m.put("avatarUrl", friend.getAvatarUrl() != null ? friend.getAvatarUrl() : "");
            m.put("signature", friend.getSignature() != null ? friend.getSignature() : "");
            // 未读消息数
            long unread = friendMessageRepository.countUnread(userId);
            m.put("unread", unread);
            // 最新一条消息
            List<FriendMessage> latest = friendMessageRepository.findLatestMessage(userId, friendId);
            if (!latest.isEmpty()) {
                FriendMessage lm = latest.get(0);
                String content = lm.getContent();
                if (content.length() > 30) content = content.substring(0, 30) + "...";
                m.put("lastMessage", content);
            } else {
                m.put("lastMessage", "");
            }
            result.add(m);
        }
        return result;
    }

    /** 获取待处理的好友申请 */
    public List<Map<String, Object>> getPendingRequests(Long userId) {
        List<Friendship> list = friendshipRepository.findPendingRequests(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friendship fs : list) {
            User fromUser = userRepository.findById(fs.getUserId()).orElse(null);
            if (fromUser == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("friendshipId", fs.getId());
            m.put("fromUserId", fromUser.getId());
            m.put("fromUsername", fromUser.getUsername());
            m.put("avatarUrl", fromUser.getAvatarUrl() != null ? fromUser.getAvatarUrl() : "");
            m.put("createdAt", fs.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    /** 发送消息 */
    @Transactional
    public FriendMessage sendMessage(Long senderId, Long friendshipId, String content) {
        Friendship fs = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> BusinessException.notFound("好友关系不存在"));
        if (!fs.getStatus().equals("ACCEPTED")) {
            throw BusinessException.badRequest("你们还不是好友");
        }
        Long receiverId = fs.getUserId().equals(senderId) ? fs.getFriendId() : fs.getUserId();

        FriendMessage msg = FriendMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .friendshipId(friendshipId)
                .content(content)
                .build();
        msg = friendMessageRepository.save(msg);

        // 发送通知
        User sender = userRepository.findById(senderId).orElse(null);
        if (sender != null) {
            notificationService.create(senderId, sender.getUsername(),
                    receiverId, "FRIEND_MESSAGE",
                    "新消息",
                    sender.getUsername() + "： " + (content.length() > 30 ? content.substring(0, 30) + "..." : content),
                    null, null);
        }
        return msg;
    }

    /** 获取聊天记录 */
    public List<Map<String, Object>> getChatHistory(Long userId, Long friendUserId) {
        List<FriendMessage> msgs = friendMessageRepository.findChatHistory(userId, friendUserId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FriendMessage m : msgs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("senderId", m.getSenderId());
            item.put("receiverId", m.getReceiverId());
            item.put("friendshipId", m.getFriendshipId());
            item.put("content", m.getContent());
            item.put("isRead", m.getIsRead());
            item.put("createdAt", m.getCreatedAt());
            item.put("isMe", m.getSenderId().equals(userId));
            result.add(item);
        }
        return result;
    }

    /** 标记消息已读 */
    @Transactional
    public void markAsRead(Long userId, Long senderId) {
        friendMessageRepository.markAsRead(userId, senderId);
    }
}
