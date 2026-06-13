package com.example.aichat.repository;

import com.example.aichat.model.Conversation;
import com.example.aichat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("SELECT c FROM Conversation c WHERE c.user.id = :userId ORDER BY c.createdAt DESC")
    List<Conversation> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}
