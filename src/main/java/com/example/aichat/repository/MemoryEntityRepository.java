package com.example.aichat.repository;

import com.example.aichat.model.MemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemoryEntityRepository extends JpaRepository<MemoryEntity, Long> {

    Optional<MemoryEntity> findByUserIdAndName(Long userId, String name);
}
