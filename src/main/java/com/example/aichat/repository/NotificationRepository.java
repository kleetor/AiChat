package com.example.aichat.repository;

import com.example.aichat.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

    long countByTargetUserIdAndIsReadFalse(Long targetUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.targetUserId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);
}
