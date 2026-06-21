package com.example.aichat.repository;

import com.example.aichat.model.FriendMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FriendMessageRepository extends JpaRepository<FriendMessage, Long> {

    /** 获取两个用户的聊天记录 */
    @Query("SELECT m FROM FriendMessage m WHERE " +
           "(m.senderId = :u1 AND m.receiverId = :u2) OR (m.senderId = :u2 AND m.receiverId = :u1) " +
           "ORDER BY m.createdAt ASC")
    List<FriendMessage> findChatHistory(@Param("u1") Long u1, @Param("u2") Long u2);

    /** 未读消息数 */
    @Query("SELECT COUNT(m) FROM FriendMessage m WHERE m.receiverId = :userId AND m.isRead = false")
    long countUnread(@Param("userId") Long userId);

    /** 标记已读 */
    @Modifying
    @Query("UPDATE FriendMessage m SET m.isRead = true WHERE m.receiverId = :userId AND m.senderId = :senderId AND m.isRead = false")
    void markAsRead(@Param("userId") Long userId, @Param("senderId") Long senderId);

    /** 两个用户之间的最新一条消息 */
    @Query("SELECT m FROM FriendMessage m WHERE " +
           "(m.senderId = :u1 AND m.receiverId = :u2) OR (m.senderId = :u2 AND m.receiverId = :u1) " +
           "ORDER BY m.createdAt DESC")
    List<FriendMessage> findLatestMessage(@Param("u1") Long u1, @Param("u2") Long u2);
}
