package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.RechargeOrder;
import com.example.aichat.repository.RechargeOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class SponsorReviewService {

    private final RechargeOrderRepository rechargeOrderRepository;
    private final BillingService billingService;

    public SponsorReviewService(RechargeOrderRepository rechargeOrderRepository, BillingService billingService) {
        this.rechargeOrderRepository = rechargeOrderRepository;
        this.billingService = billingService;
    }

    public Page<RechargeOrder> getSponsorReviews(String reviewStatus, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (reviewStatus != null && !reviewStatus.isEmpty()) {
            return rechargeOrderRepository.findByReviewStatus(reviewStatus, pageable);
        }
        return rechargeOrderRepository.findAll(pageable);
    }

    @Transactional
    public RechargeOrder approveSponsor(Long orderId, BigDecimal tokens, String comment, Long reviewerId) {
        RechargeOrder order = rechargeOrderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        if (!"PENDING".equals(order.getReviewStatus())) {
            throw BusinessException.conflict("该订单已审核过");
        }
        order.setReviewStatus("APPROVED");
        order.setReviewComment(comment);
        order.setReviewerId(reviewerId);
        order.setReviewedAt(LocalDateTime.now());
        rechargeOrderRepository.save(order);

        billingService.adminRecharge(order.getUserId(), tokens, "赞助审核通过: " + comment, reviewerId);
        return order;
    }

    @Transactional
    public RechargeOrder rejectSponsor(Long orderId, String comment, Long reviewerId) {
        RechargeOrder order = rechargeOrderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        if (!"PENDING".equals(order.getReviewStatus())) {
            throw BusinessException.conflict("该订单已审核过");
        }
        order.setReviewStatus("REJECTED");
        order.setReviewComment(comment);
        order.setReviewerId(reviewerId);
        order.setReviewedAt(LocalDateTime.now());
        return rechargeOrderRepository.save(order);
    }
}
