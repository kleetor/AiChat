package com.example.aichat.controller;

import com.example.aichat.model.MemoryItem;
import com.example.aichat.service.MemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 长期记忆管理 REST API。
 * 所有接口通过 JWT 认证获取 userId，不接收外部传入的 userId。
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    private Long getUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    /** 获取用户所有记忆 (时间倒序) */
    @GetMapping("/list")
    public List<MemoryItem> list(Authentication auth) {
        return memoryService.listAll(getUserId(auth));
    }

    /** 获取已启用的记忆 */
    @GetMapping("/enabled")
    public List<MemoryItem> enabled(Authentication auth) {
        return memoryService.listEnabled(getUserId(auth));
    }

    /** 手动添加记忆 */
    @PostMapping("/add")
    public MemoryItem add(@RequestBody Map<String, Object> body, Authentication auth) {
        String value = (String) body.get("value");
        return memoryService.addManual(getUserId(auth), value);
    }

    /** 编辑记忆 */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                     @RequestBody Map<String, String> body,
                                     Authentication auth) {
        String value = body.get("value");
        if (value == null || value.isBlank()) {
            return ResponseEntity.badRequest().body("value 不能为空");
        }
        memoryService.update(id, getUserId(auth), value);
        return ResponseEntity.ok().build();
    }

    /** 启用/禁用记忆 */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggle(@PathVariable Long id,
                                     @RequestParam boolean enabled,
                                     Authentication auth) {
        memoryService.toggleEnabled(id, getUserId(auth), enabled);
        return ResponseEntity.ok().build();
    }

    /** 删除记忆 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    Authentication auth) {
        memoryService.delete(id, getUserId(auth));
        return ResponseEntity.ok().build();
    }

    /** 清空全部记忆 */
    @DeleteMapping("/clear")
    public ResponseEntity<?> clear(Authentication auth) {
        memoryService.deleteAll(getUserId(auth));
        return ResponseEntity.ok().build();
    }

    /** 手动检索记忆 (模式3)，promptId 可选用于角色隔离 */
    @PostMapping("/search")
    public List<MemoryItem> search(@RequestBody Map<String, Object> body, Authentication auth) {
        String query = (String) body.get("query");
        Long promptId = body.get("promptId") instanceof Number
                ? ((Number) body.get("promptId")).longValue() : null;
        return memoryService.searchAndRecall(getUserId(auth), query, promptId);
    }
}
