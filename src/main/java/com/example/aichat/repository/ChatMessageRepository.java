package com.example.aichat.repository;

import com.example.aichat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);

    int countByConversationId(Long conversationId);

    /** 校验消息归属，避免 lazy load User 实体 */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.id = :id AND m.user.id = :userId")
    long countByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 获取会话历史消息，按 promptId 过滤（共享=所有提示词可见，私有=仅该提示词可见） */
    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId " +
           "AND (m.promptId IS NULL OR m.promptId = :promptId) ORDER BY m.timestamp ASC")
    List<ChatMessage> findByConversationIdAndPromptAccessible(
            @Param("conversationId") Long conversationId,
            @Param("promptId") Long promptId);
}
