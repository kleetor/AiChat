package com.example.aichat.repository;

import com.example.aichat.model.TokenUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {
    Page<TokenUsage> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT SUM(t.costAmount) FROM TokenUsage t WHERE t.userId = :userId")
    BigDecimal sumCostByUserId(@Param("userId") Long userId);

    @Query("SELECT SUM(t.totalTokens) FROM TokenUsage t WHERE t.userId = :userId")
    Long sumTotalTokensByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(t.costAmount), 0) FROM TokenUsage t WHERE t.createdAt BETWEEN :start AND :end")
    BigDecimal sumCostBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT t FROM TokenUsage t WHERE (:userId IS NULL OR t.userId = :userId) AND t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt DESC")
    Page<TokenUsage> findByFilters(@Param("userId") Long userId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    Pageable pageable);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(t) FROM TokenUsage t WHERE t.createdAt BETWEEN :start AND :end")
    long countTodayMessages(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}