package com.example.aichat.repository;

import com.example.aichat.model.MemoryRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryRelationRepository extends JpaRepository<MemoryRelation, Long> {

    List<MemoryRelation> findBySubjectId(Long subjectId);

    List<MemoryRelation> findByObjectId(Long objectId);

    List<MemoryRelation> findByUserId(Long userId);

    List<MemoryRelation> findBySourceItemId(Long sourceItemId);
}
