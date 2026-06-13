package com.example.aichat.controller;

import com.example.aichat.model.PromptsHub;
import com.example.aichat.service.PromptsHubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts-hub")
public class PromptsHubController {

    @Autowired
    private PromptsHubService promptsHubService;

    @GetMapping
    public ResponseEntity<List<PromptsHub>> getAllPrompts() {
        return ResponseEntity.ok(promptsHubService.getAllPrompts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromptsHub> getPromptById(@PathVariable Long id) {
        return ResponseEntity.ok(promptsHubService.getPromptById(id));
    }

    @GetMapping("/user")
    public ResponseEntity<List<PromptsHub>> getUserUploadedPrompts(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(promptsHubService.getUserUploadedPrompts(userId));
    }

    @PostMapping("/upload")
    public ResponseEntity<PromptsHub> uploadPrompt(@RequestBody Map<String, String> body,
                                                  Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String name = body.get("name");
        String content = body.get("content");
        String userMessage = body.get("userMessage");

        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        PromptsHub prompt = promptsHubService.uploadPrompt(userId, name, content, userMessage);
        return ResponseEntity.ok(prompt);
    }

    @PostMapping("/upload-with-image")
    public ResponseEntity<PromptsHub> uploadPromptWithImage(
            @RequestParam("name") String name,
            @RequestParam("content") String content,
            @RequestParam(value = "userMessage", required = false) String userMessage,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (name == null || name.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            PromptsHub prompt = promptsHubService.uploadPromptWithImage(userId, name, content, userMessage, image);
            return ResponseEntity.ok(prompt);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<Map<String, String>> uploadImageForPrompt(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image,
            Authentication auth) {
        try {
            PromptsHub prompt = promptsHubService.updateImageUrl(id, image);
            return ResponseEntity.ok(Map.of("imageUrl", prompt.getImageUrl()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<?> likePrompt(@PathVariable Long id) {
        promptsHubService.likePrompt(id);
        return ResponseEntity.ok().build();
    }
}
