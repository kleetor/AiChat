package com.example.aichat.repository;

import com.example.aichat.model.MemoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryItemEntityRepository extends JpaRepository<MemoryItemEntity, Long> {

    @Query("SELECT mie.entityId FROM MemoryItemEntity mie WHERE mie.memoryItemId = :itemId")
    List<Long> findEntityIdsByMemoryId(@Param("itemId") Long itemId);

    @Query("SELECT mie.memoryItemId FROM MemoryItemEntity mie WHERE mie.entityId = :entityId")
    List<Long> findMemoryIdsByEntityId(@Param("entityId") Long entityId);

    void deleteByMemoryItemId(Long memoryItemId);

    /** 优化6: 实体合并 — 删除会产生唯一约束冲突的行 */
    @Modifying
    @Query(value = """
        DELETE FROM memory_item_entities
        WHERE entity_id = :fromId
        AND (memory_item_id, role) IN (
            SELECT * FROM (
                SELECT memory_item_id, role FROM memory_item_entities WHERE entity_id = :toId
            ) t
        )
        """, nativeQuery = true)
    int deleteConflicting(@Param("fromId") Long fromId, @Param("toId") Long toId);

    /** 优化6: 实体合并 — 将剩余关联转移到目标实体 */
    @Modifying
    @Query("UPDATE MemoryItemEntity mie SET mie.entityId = :toId WHERE mie.entityId = :fromId")
    int updateEntityId(@Param("fromId") Long fromId, @Param("toId") Long toId);
}
