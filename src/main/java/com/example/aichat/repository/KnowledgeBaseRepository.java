package com.example.aichat.repository;

import com.example.aichat.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {
    List<KnowledgeBase> findByUserId(Long userId);

    @Modifying
    @Query("UPDATE KnowledgeBase k SET k.docCount = k.docCount + :docDelta, " +
           "k.chunkCount = k.chunkCount + :chunkDelta, " +
           "k.totalSize = k.totalSize + :sizeDelta, k.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE k.id = :id")
    void incrementCounts(@Param("id") Long id, @Param("docDelta") int docDelta,
                         @Param("chunkDelta") int chunkDelta, @Param("sizeDelta") long sizeDelta);

    @Modifying
    @Query("UPDATE KnowledgeBase k SET k.docCount = k.docCount - :docDelta, " +
           "k.chunkCount = k.chunkCount - :chunkDelta, " +
           "k.totalSize = k.totalSize - :sizeDelta, k.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE k.id = :id")
    void decrementCounts(@Param("id") Long id, @Param("docDelta") int docDelta,
                         @Param("chunkDelta") int chunkDelta, @Param("sizeDelta") long sizeDelta);
}
