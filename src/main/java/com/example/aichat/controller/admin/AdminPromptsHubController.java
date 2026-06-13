package com.example.aichat.controller.admin;

import com.example.aichat.model.PromptsHub;
import com.example.aichat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/prompts-hub")
public class AdminPromptsHubController {

    @Autowired
    private AdminService adminService;

    @GetMapping
    public ResponseEntity<Page<PromptsHub>> getPromptsHub(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(adminService.getPromptsHub(keyword, page, size));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        adminService.deletePromptHub(id);
        return ResponseEntity.ok(Map.of("message", "提示词已删除"));
    }

    @PutMapping("/{id}/feature")
    public ResponseEntity<?> setFeatured(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean featured = body.get("featured");
        adminService.setFeatured(id, featured);
        return ResponseEntity.ok(Map.of("message", "精选状态已更新"));
    }
}
