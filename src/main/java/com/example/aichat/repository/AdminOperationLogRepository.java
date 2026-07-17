package com.example.aichat.repository;

import com.example.aichat.model.AdminOperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface AdminOperationLogRepository extends JpaRepository<AdminOperationLog, Long> {

    Page<AdminOperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminOperationLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    Page<AdminOperationLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM AdminOperationLog a WHERE a.createdAt < :before")
    int deleteOlderThan(LocalDateTime before);
}
