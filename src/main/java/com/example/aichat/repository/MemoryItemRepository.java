package com.example.aichat.repository;

import com.example.aichat.model.MemoryItem;
import com.example.aichat.model.MemoryItem.DetailLevel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryItemRepository extends JpaRepository<MemoryItem, Long> {

    List<MemoryItem> findByUserIdOrderByLastAccessedAtDesc(Long userId);

    List<MemoryItem> findByUserIdAndEnabledTrue(Long userId);

    Optional<MemoryItem> findByChromaId(String chromaId);

    List<MemoryItem> findByChromaIdIn(List<String> chromaIds);

    /** 获取最近N条特定层级且启用的记忆 (用于模式2注入) */
    @Query("SELECT m FROM MemoryItem m WHERE m.userId = :userId " +
           "AND m.enabled = true AND m.detailLevel IN :levels " +
           "ORDER BY m.lastAccessedAt DESC")
    List<MemoryItem> findTopNEnabled(@Param("userId") Long userId,
                                     @Param("levels") List<DetailLevel> levels,
                                     Pageable pageable);

    /** 批量更新 lastAccessedAt 和 accessCount (避免 N+1) */
    @Modifying
    @Query("UPDATE MemoryItem m SET m.lastAccessedAt = :now, " +
           "m.accessCount = m.accessCount + 1 WHERE m.id IN :ids")
    int batchTouch(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
}
