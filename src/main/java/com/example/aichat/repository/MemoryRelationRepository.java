package com.example.aichat.repository;

import com.example.aichat.model.MemoryRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryRelationRepository extends JpaRepository<MemoryRelation, Long> {

    List<MemoryRelation> findBySubjectId(Long subjectId);

    List<MemoryRelation> findByObjectId(Long objectId);

    List<MemoryRelation> findByUserId(Long userId);

    List<MemoryRelation> findBySourceItemId(Long sourceItemId);

    /** 优化1: 查重 — 检查是否已存在相同三元组 */
    Optional<MemoryRelation> findBySubjectIdAndPredicateAndObjectId(Long subjectId, String predicate, Long objectId);

    /** 优化2: 将指定 sourceItem 的所有关系标记为过期 */
    @Modifying
    @Query("UPDATE MemoryRelation r SET r.validUntil = :now WHERE r.sourceItemId = :itemId AND r.validUntil IS NULL")
    int expireBySourceItemId(@Param("itemId") Long itemId, @Param("now") LocalDateTime now);

    /** 优化6: 实体合并 — 批量更新 subject_id */
    @Modifying
    @Query("UPDATE MemoryRelation r SET r.subjectId = :toId WHERE r.subjectId = :fromId")
    int updateSubjectId(@Param("fromId") Long fromId, @Param("toId") Long toId);

    /** 优化6: 实体合并 — 批量更新 object_id */
    @Modifying
    @Query("UPDATE MemoryRelation r SET r.objectId = :toId WHERE r.objectId = :fromId")
    int updateObjectId(@Param("fromId") Long fromId, @Param("toId") Long toId);
}
