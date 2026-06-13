package com.example.aichat.repository;

import com.example.aichat.model.RechargeOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, Long> {
    Optional<RechargeOrder> findByOrderNo(String orderNo);

    Page<RechargeOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<RechargeOrder> findByStatus(String status);

    Page<RechargeOrder> findByReviewStatus(String reviewStatus, Pageable pageable);

    List<RechargeOrder> findByReviewStatus(String reviewStatus);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(r.amount), 0) FROM RechargeOrder r WHERE r.status = 'SUCCESS' AND r.paidAt BETWEEN :start AND :end")
    BigDecimal sumRevenueBetween(@org.springframework.data.repository.query.Param("start") LocalDateTime start,
                                  @org.springframework.data.repository.query.Param("end") LocalDateTime end);
}