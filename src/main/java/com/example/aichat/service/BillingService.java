package com.example.aichat.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillingService {

    private static final Logger logger = LoggerFactory.getLogger(BillingService.class);
    private static final BigDecimal SAFETY_MULTIPLIER = new BigDecimal("2.0");

    /** 预扣金额映射：userId -> 预估费用，用于 checkAndReserveBalance / deductTokens 之间传递 */
    private final ConcurrentHashMap<Long, BigDecimal> reservations = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final RechargeOrderRepository rechargeOrderRepository;

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
    public void checkAndReserveBalance(Long userId, Long modelConfigId, Long estimatedInputTokens) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        ModelConfig config = modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));

        BigDecimal estimatedCost = calculateEstimatedCost(config, estimatedInputTokens);
        
        if (user.getBalance().compareTo(estimatedCost) < 0) {
            throw new InsufficientBalanceException("余额不足，请充值后再使用");
        }

        // 预扣预估费用，防止并发请求导致余额超支
        user.setBalance(user.getBalance().subtract(estimatedCost));
        userRepository.save(user);
        reservations.put(userId, estimatedCost);
        logger.debug("预扣余额: userId={}, amount={}", userId, estimatedCost);
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
    public TokenUsage deductTokens(Long userId, Long modelConfigId, 
                                   Long inputTokens, Long outputTokens, 
                                   Long conversationId) {
        int retryCount = 3;
        while (retryCount > 0) {
            try {
                return doDeductTokens(userId, modelConfigId, inputTokens, outputTokens, conversationId);
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

    @Transactional
    protected TokenUsage doDeductTokens(Long userId, Long modelConfigId,
                                         Long inputTokens, Long outputTokens,
                                         Long conversationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        ModelConfig config = modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));

        // 退还 checkAndReserveBalance 中预扣的预估费用
        BigDecimal reservedAmount = reservations.remove(userId);
        BigDecimal effectiveBalance = reservedAmount != null
                ? user.getBalance().add(reservedAmount)
                : user.getBalance();

        BigDecimal cost = calculateCost(config, inputTokens, outputTokens);
        
        if (effectiveBalance.compareTo(cost) < 0) {
            if (reservedAmount != null) {
                reservations.put(userId, reservedAmount);
            }
            throw new InsufficientBalanceException("余额不足");
        }

        BigDecimal balanceBefore = effectiveBalance.setScale(4, RoundingMode.HALF_UP);
        BigDecimal balanceAfter = effectiveBalance.subtract(cost).setScale(4, RoundingMode.HALF_UP);
        user.setBalance(balanceAfter);
        userRepository.save(user);

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
    public RechargeOrder recharge(Long userId, BigDecimal amount, String payChannel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

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

    public BigDecimal getUserBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return user.getBalance();
    }

    public BigDecimal getTotalSpent(Long userId) {
        BigDecimal sum = tokenUsageRepository.sumCostByUserId(userId);
        return sum != null ? sum.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

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
    public RechargeOrder adminRecharge(Long userId, BigDecimal amount, String reason, Long reviewerId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

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
                .orElseThrow(() -> new RuntimeException("用户不存在"));

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