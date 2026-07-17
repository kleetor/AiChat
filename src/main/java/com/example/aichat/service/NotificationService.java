package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.Notification;
import com.example.aichat.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    /** 创建通知（自己触发的操作不发给自己） */
    public void create(Long fromUserId, String fromUserName,
                       Long targetUserId, String type,
                       String title, String content,
                       Long promptId, Long commentId) {
        if (fromUserId != null && fromUserId.equals(targetUserId)) return; // 不给自己的操作发通知
        Notification n = Notification.builder()
                .targetUserId(targetUserId)
                .type(type)
                .title(title)
                .content(content)
                .promptId(promptId)
                .commentId(commentId)
                .fromUserName(fromUserName)
                .build();
        notificationRepository.save(n);
    }

    /** 获取某用户的通知列表 */
    public List<Notification> getNotifications(Long userId) {
        return notificationRepository.findByTargetUserIdOrderByCreatedAtDesc(userId);
    }

    /** 未读数 */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByTargetUserIdAndIsReadFalse(userId);
    }

    /** 全部标记已读 */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    /** 单条标记已读 */
    @Transactional
    public void markAsRead(Long id, Long userId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("通知不存在"));
        if (!n.getTargetUserId().equals(userId)) {
            throw BusinessException.forbidden("无权操作");
        }
        n.setIsRead(true);
        notificationRepository.save(n);
    }

    /** 删除单条通知 */
    @Transactional
    public void delete(Long id, Long userId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("通知不存在"));
        if (!n.getTargetUserId().equals(userId)) {
            throw BusinessException.forbidden("无权操作");
        }
        notificationRepository.delete(n);
    }
}
