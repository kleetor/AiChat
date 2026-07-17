package com.example.aichat.repository;

import com.example.aichat.model.TokenBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface TokenBlacklistEntryRepository extends JpaRepository<TokenBlacklistEntry, Long> {

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("DELETE FROM TokenBlacklistEntry t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
