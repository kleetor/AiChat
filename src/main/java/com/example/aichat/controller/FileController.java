package com.example.aichat.controller;

import com.example.aichat.service.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传控制器 —— 工具调用路径专用。
 * 仅上传到 S3 并返回 URL，不做识别（识别由 LLM 通过工具调用触发）。
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private ImageService imageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "请选择文件"));
        }

        // 目前仅支持图片类型，日后扩展其他类型时移除此限制
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.status(400).body(Map.of("error", "目前仅支持图片文件"));
        }

        try {
            String fileUrl = imageService.uploadFileOnly(file);
            String fileName = file.getOriginalFilename();
            Map<String, String> response = new HashMap<>();
            response.put("fileUrl", fileUrl);
            response.put("fileName", fileName != null ? fileName : "unknown");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("文件上传失败", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "文件上传失败，请稍后重试");
            return ResponseEntity.status(500).body(error);
        }
    }
}
