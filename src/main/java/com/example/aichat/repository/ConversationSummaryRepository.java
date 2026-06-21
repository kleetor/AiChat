package com.example.aichat.repository;

import com.example.aichat.model.ConversationSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, Long> {
    ConversationSummary findByConversationId(Long conversationId);
}
