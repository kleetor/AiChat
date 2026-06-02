package com.example.aichat.repository;

import com.example.aichat.model.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
}
