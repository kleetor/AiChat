package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.model.RechargeOrder;
import com.example.aichat.model.TokenUsage;
import com.example.aichat.model.User;
import com.example.aichat.repository.ModelConfigRepository;
import com.example.aichat.repository.RechargeOrderRepository;
import com.example.aichat.repository.TokenUsageRepository;
import com.example.aichat.repository.UserRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
public class BillingService {

    private static final Logger logger = LoggerFactory.getLogger(BillingService.class);
    private static final BigDecimal SAFETY_MULTIPLIER = new BigDecimal("2.0");

    private final UserRepository userRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final RechargeOrderRepository rechargeOrderRepository;

    // 自注入以支持 REQUIRES_NEW 事务传播（绕过 Spring AOP 自调用限制）
    @Lazy
    @Autowired
    private BillingService self;

    @Autowired
    public BillingService(UserRepository userRepository,
                         ModelConfigRepository modelConfigRepository,
                         TokenUsageRepository tokenUsageRepository,
                         RechargeOrderRepository rechargeOrderRepository) {
        this.userRepository = userRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.tokenUsageRepository = tokenUsageRepository;
        this.rechargeOrderRepository = rechargeOrderRepository;
    }

    @Transactional
    @CacheEvict(value = "billingBalance", key = "#userId")
    public void checkAndReserveBalance(Long userId, Long modelConfigId, Long estimatedInputTokens) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        ModelConfig config = modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> BusinessException.notFound("模型配置不存在"));

        BigDecimal estimatedCost = calculateEstimatedCost(config, estimatedInputTokens);
        
        // 可用余额 = balance（不含预留）
        if (user.getBalance().compareTo(estimatedCost) < 0) {
            throw new InsufficientBalanceException("余额不足，请充值后再使用");
        }

        // 从 balance 转入 reserved_balance，均在 DB 中记录
        user.setBalance(user.getBalance().subtract(estimatedCost));
        user.setReservedBalance(user.getReservedBalance().add(estimatedCost));
        userRepository.save(user);
        logger.debug("预扣余额(DB): userId={}, amount={}, balance={}, reserved={}", 
                userId, estimatedCost, user.getBalance(), user.getReservedBalance());
    }

    /**
     * 释放用户的预留余额，归还至可用余额。
     * 用于 LLM 调用成功但后续扣费失败时进行清理。
     */
    @Transactional
    @CacheEvict(value = "billingBalance", key = "#userId")
    public void releaseReservedBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        BigDecimal reserved = user.getReservedBalance();
        if (reserved.compareTo(BigDecimal.ZERO) > 0) {
            user.setBalance(user.getBalance().add(reserved));
            user.setReservedBalance(BigDecimal.ZERO);
            userRepository.save(user);
            logger.debug("释放预留余额: userId={}, amount={}, balance={}", userId, reserved, user.getBalance());
        }
    }

    private BigDecimal calculateEstimatedCost(ModelConfig config, Long estimatedInputTokens) {
        BigDecimal inputCost = config.getInputTokenPrice()
                .multiply(BigDecimal.valueOf(estimatedInputTokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        
        BigDecimal outputEstimate = BigDecimal.valueOf(estimatedInputTokens).multiply(SAFETY_MULTIPLIER);
        BigDecimal outputCost = config.getOutputTokenPrice()
                .multiply(outputEstimate)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        
        return inputCost.add(outputCost);
    }

    @Transactional
    @CacheEvict(value = {"billingBalance", "billingSpent", "billingTokens"}, key = "#userId")
    public TokenUsage deductTokens(Long userId, Long modelConfigId, 
                                   Long inputTokens, Long outputTokens, 
                                   Long conversationId) {
        int retryCount = 3;
        while (retryCount > 0) {
            try {
                return self.doDeductTokens(userId, modelConfigId, inputTokens, outputTokens, conversationId);
            } catch (OptimisticLockException e) {
                retryCount--;
                logger.warn("余额扣减冲突，重试次数: {}", retryCount);
                if (retryCount == 0) {
                    throw new RuntimeException("扣费失败，请稍后重试");
                }
            }
        }
        throw new RuntimeException("扣费失败");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected TokenUsage doDeductTokens(Long userId, Long modelConfigId,
                                         Long inputTokens, Long outputTokens,
                                         Long conversationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        ModelConfig config = modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> BusinessException.notFound("模型配置不存在"));

        BigDecimal cost = calculateCost(config, inputTokens, outputTokens);
        
        // return reserved_amount to balance first, then deduct actual cost
        BigDecimal reservedAmount = user.getReservedBalance();
        BigDecimal usableBalance = user.getBalance().add(reservedAmount);
        
        if (usableBalance.compareTo(cost) < 0) {
            // 归还 reservedBalance 到 balance，避免余额被锁定
            user.setBalance(user.getBalance().add(reservedAmount));
            user.setReservedBalance(BigDecimal.ZERO);
            userRepository.save(user);
            logger.warn("扣费失败，已归还预留余额: userId={}, reservedAmount={}, balance={}",
                    userId, reservedAmount, user.getBalance());
            throw new InsufficientBalanceException("余额不足");
        }

        BigDecimal balanceBefore = usableBalance.setScale(4, RoundingMode.HALF_UP);
        BigDecimal balanceAfter = usableBalance.subtract(cost).setScale(4, RoundingMode.HALF_UP);
        
        // Update: clear reserved_balance, set balance to after-cost
        user.setReservedBalance(BigDecimal.ZERO);
        user.setBalance(balanceAfter);
        userRepository.save(user);

        logger.debug("实际扣费: userId={}, cost={}, reserved={}, balanceAfter={}", 
                userId, cost, reservedAmount, balanceAfter);

        TokenUsage usage = TokenUsage.builder()
                .userId(userId)
                .conversationId(conversationId)
                .modelConfigId(modelConfigId)
                .modelName(config.getModelName())
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .costAmount(cost.setScale(4, RoundingMode.HALF_UP))
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .build();
        
        return tokenUsageRepository.save(usage);
    }

    private BigDecimal calculateCost(ModelConfig config, Long inputTokens, Long outputTokens) {
        BigDecimal inputCost = config.getInputTokenPrice()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        
        BigDecimal outputCost = config.getOutputTokenPrice()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        
        return inputCost.add(outputCost).setScale(4, RoundingMode.HALF_UP);
    }

    @Transactional
    @CacheEvict(value = "billingBalance", key = "#userId")
    public RechargeOrder recharge(Long userId, BigDecimal amount, String payChannel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        String orderNo = generateOrderNo();
        
        RechargeOrder order = RechargeOrder.builder()
                .userId(userId)
                .orderNo(orderNo)
                .amount(amount)
                .status("SUCCESS")
                .payChannel(payChannel)
                .paidAt(LocalDateTime.now())
                .build();
        
        rechargeOrderRepository.save(order);

        BigDecimal newBalance = user.getBalance().add(amount).setScale(4, RoundingMode.HALF_UP);
        user.setBalance(newBalance);
        userRepository.save(user);

        logger.info("用户 {} 充值成功，金额: {}, 订单号: {}", userId, amount, orderNo);
        return order;
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(10000);
        return "RC" + timestamp + String.format("%04d", random);
    }

    @Cacheable(value = "billingBalance", key = "#userId")
    public BigDecimal getUserBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        return user.getBalance().add(user.getReservedBalance());
    }

    @Cacheable(value = "billingSpent", key = "#userId")
    public BigDecimal getTotalSpent(Long userId) {
        BigDecimal sum = tokenUsageRepository.sumCostByUserId(userId);
        return sum != null ? sum.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    @Cacheable(value = "billingTokens", key = "#userId")
    public Long getTotalTokens(Long userId) {
        Long sum = tokenUsageRepository.sumTotalTokensByUserId(userId);
        return sum != null ? sum : 0L;
    }

    public static class InsufficientBalanceException extends RuntimeException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }

    @Transactional
    @CacheEvict(value = "billingBalance", key = "#userId")
    public RechargeOrder adminRecharge(Long userId, BigDecimal amount, String reason, Long reviewerId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        String orderNo = generateOrderNo();

        RechargeOrder order = RechargeOrder.builder()
                .userId(userId)
                .orderNo(orderNo)
                .amount(amount)
                .status("SUCCESS")
                .payChannel("MANUAL")
                .reviewStatus("APPROVED")
                .reviewComment(reason)
                .reviewerId(reviewerId)
                .reviewedAt(LocalDateTime.now())
                .paidAt(LocalDateTime.now())
                .build();

        rechargeOrderRepository.save(order);

        BigDecimal newBalance = user.getBalance().add(amount).setScale(4, RoundingMode.HALF_UP);
        user.setBalance(newBalance);
        userRepository.save(user);

        logger.info("管理员 {} 手动调整用户 {} 余额: {}, 原因: {}", reviewerId, userId, amount, reason);
        return order;
    }

    @Transactional
    public RechargeOrder createSponsorOrder(Long userId, BigDecimal amount, String sponsorImagePath) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        String orderNo = "SP" + generateOrderNo().substring(2);

        RechargeOrder order = RechargeOrder.builder()
                .userId(userId)
                .userPid(user.getPid())
                .userName(user.getUsername())
                .orderNo(orderNo)
                .amount(amount)
                .status("PENDING")
                .payChannel("SPONSOR")
                .sponsorImagePath(sponsorImagePath)
                .reviewStatus("PENDING")
                .build();

        RechargeOrder saved = rechargeOrderRepository.save(order);
        logger.info("赞助审核订单已创建: userId={}, pid={}, amount={}", userId, user.getPid(), amount);
        return saved;
    }
}