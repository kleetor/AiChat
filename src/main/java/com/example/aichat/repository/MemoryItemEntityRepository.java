package com.example.aichat.repository;

import com.example.aichat.model.MemoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
