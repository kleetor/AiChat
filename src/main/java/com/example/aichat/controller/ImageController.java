package com.example.aichat.controller;

import com.example.aichat.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
                                         Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "请选择图片文件"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.status(400).body(Map.of("error", "只支持图片文件"));
        }

        try {
            ImageService.ImageUploadResult result = imageService.uploadAndRecognize(file);
            Map<String, String> response = new HashMap<>();
            response.put("imageUrl", result.getImageUrl());
            response.put("description", result.getFormattedDescription());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "图片处理失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
