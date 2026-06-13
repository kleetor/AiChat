package com.example.aichat.controller.admin;

import com.example.aichat.model.ModelConfig;
import com.example.aichat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/model-configs")
public class AdminModelConfigController {

    @Autowired
    private AdminService adminService;

    @GetMapping
    public ResponseEntity<List<ModelConfig>> getAll() {
        return ResponseEntity.ok(adminService.getModelConfigs());
    }

    @PostMapping
    public ResponseEntity<ModelConfig> create(@RequestBody ModelConfig config) {
        return ResponseEntity.ok(adminService.createModelConfig(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfig> update(@PathVariable Long id, @RequestBody ModelConfig config) {
        return ResponseEntity.ok(adminService.updateModelConfig(id, config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        adminService.deleteModelConfig(id);
        return ResponseEntity.ok(Map.of("message", "模型配置已删除"));
    }
}
