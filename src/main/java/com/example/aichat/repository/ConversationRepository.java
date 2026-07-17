package com.example.aichat.repository;

import com.example.aichat.model.Conversation;
import com.example.aichat.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("SELECT c FROM Conversation c WHERE c.user.id = :userId ORDER BY c.createdAt DESC")
    List<Conversation> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c WHERE c.user.id = :userId ORDER BY c.createdAt DESC")
    Page<Conversation> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.user.id = :userId")
    long countByUserIdForUpdate(@Param("userId") Long userId);
}
