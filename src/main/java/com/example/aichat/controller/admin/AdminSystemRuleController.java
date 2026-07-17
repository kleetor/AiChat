package com.example.aichat.controller.admin;

import com.example.aichat.dto.SystemRuleRequest;
import com.example.aichat.model.SystemRule;
import com.example.aichat.service.AdminSystemRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system-rules")
public class AdminSystemRuleController {

    private final AdminSystemRuleService service;

    public AdminSystemRuleController(AdminSystemRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SystemRule>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<SystemRule> create(@Valid @RequestBody SystemRuleRequest body) {
        return ResponseEntity.ok(service.createFromRequest(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SystemRule> update(@PathVariable Long id, @Valid @RequestBody SystemRuleRequest body) {
        return ResponseEntity.ok(service.updateFromRequest(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    /** 切换启用/禁用 */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<SystemRule> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggle(id));
    }

    /** 批量更新排序 */
    @PutMapping("/sort")
    public ResponseEntity<?> updateSort(@RequestBody List<Map<String, Object>> items) {
        service.updateSort(items);
        return ResponseEntity.ok(Map.of("message", "排序已更新"));
    }
}
