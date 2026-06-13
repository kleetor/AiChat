package com.example.aichat.controller;

import com.example.aichat.model.RechargeOrder;
import com.example.aichat.model.TokenUsage;
import com.example.aichat.repository.TokenUsageRepository;
import com.example.aichat.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        
        BigDecimal balance = billingService.getUserBalance(userId);
        BigDecimal totalSpent = billingService.getTotalSpent(userId);
        Long totalTokens = billingService.getTotalTokens(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("balance", balance);
        result.put("totalSpent", totalSpent);
        result.put("totalTokens", totalTokens);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/usage-records")
    public ResponseEntity<Page<TokenUsage>> getUsageRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);
        Page<TokenUsage> records = tokenUsageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return ResponseEntity.ok(records);
    }

    /**
     * @deprecated 模拟充值接口已关闭。充值改为赞助+人工审核模式，请使用 POST /api/billing/sponsor-upload 上传赞助凭证。
     */
    @Deprecated
    @PostMapping("/recharge")
    public ResponseEntity<Map<String, Object>> recharge(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        return ResponseEntity.status(410).body(Map.of(
            "error", "GONE",
            "message", "模拟充值已关闭。请通过赞助功能上传凭证，管理员审核后发放 Token。"
        ));
    }

    private static final String SPONSOR_UPLOAD_DIR = "uploads/upStorepic/";

    @PostMapping("/sponsor-upload")
    public ResponseEntity<Map<String, Object>> sponsorUpload(
            @RequestParam("image") MultipartFile image,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Map<String, Object> result = new HashMap<>();

        if (image == null || image.isEmpty()) {
            result.put("success", false);
            result.put("message", "请选择要上传的图片");
            return ResponseEntity.badRequest().body(result);
        }

        String contentType = image.getContentType();
        if (contentType == null || 
            (!contentType.equals("image/png") && 
             !contentType.equals("image/jpeg") && 
             !contentType.equals("image/jpg") && 
             !contentType.equals("image/gif"))) {
            result.put("success", false);
            result.put("message", "仅支持 PNG / JPG / GIF 格式的图片");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            File uploadDir = new File(SPONSOR_UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            String originalFilename = image.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;
            Path filePath = Paths.get(SPONSOR_UPLOAD_DIR + newFilename);
            Files.copy(image.getInputStream(), filePath);

            String relativePath = "/uploads/upStorepic/" + newFilename;

            result.put("success", true);
            result.put("filePath", relativePath);
            result.put("message", "上传成功，请等待管理员审核后发放 Token");
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "文件上传失败，请稍后重试");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 创建赞助审核订单：上传截图 + 填写金额后统一提交
     */
    @PostMapping("/sponsor-create")
    public ResponseEntity<Map<String, Object>> sponsorCreate(
            @RequestParam("image") MultipartFile image,
            @RequestParam("amount") BigDecimal amount,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Map<String, Object> result = new HashMap<>();

        // 校验金额
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            result.put("success", false);
            result.put("message", "请输入有效的赞助金额");
            return ResponseEntity.badRequest().body(result);
        }

        // 校验图片
        if (image == null || image.isEmpty()) {
            result.put("success", false);
            result.put("message", "请选择要上传的赞助截图");
            return ResponseEntity.badRequest().body(result);
        }

        String contentType = image.getContentType();
        if (contentType == null ||
            (!contentType.equals("image/png") &&
             !contentType.equals("image/jpeg") &&
             !contentType.equals("image/jpg") &&
             !contentType.equals("image/gif"))) {
            result.put("success", false);
            result.put("message", "仅支持 PNG / JPG / GIF 格式的图片");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            File uploadDir = new File(SPONSOR_UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            String originalFilename = image.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;
            Path filePath = Paths.get(SPONSOR_UPLOAD_DIR + newFilename);
            Files.copy(image.getInputStream(), filePath);

            String relativePath = "/uploads/upStorepic/" + newFilename;

            // 创建赞助审核订单
            RechargeOrder order = billingService.createSponsorOrder(userId, amount, relativePath);

            result.put("success", true);
            result.put("message", "赞助审核已提交，请等待管理员审核后发放 Token");
            result.put("orderId", order.getId());
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "文件上传失败，请稍后重试");
            return ResponseEntity.status(500).body(result);
        } catch (RuntimeException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}