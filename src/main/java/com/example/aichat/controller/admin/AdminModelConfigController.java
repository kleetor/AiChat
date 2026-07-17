package com.example.aichat.controller.admin;

import com.example.aichat.dto.ModelConfigDTO;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/model-configs")
public class AdminModelConfigController {

    private final AdminService adminService;

    public AdminModelConfigController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<ModelConfigDTO>> getAll() {
        return ResponseEntity.ok(
                adminService.getModelConfigs().stream()
                        .map(ModelConfigDTO::from)
                        .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<ModelConfig> create(@Valid @RequestBody ModelConfig config) {
        return ResponseEntity.ok(adminService.createModelConfig(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfig> update(@PathVariable Long id, @Valid @RequestBody ModelConfig config) {
        return ResponseEntity.ok(adminService.updateModelConfig(id, config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        adminService.deleteModelConfig(id);
        return ResponseEntity.ok(Map.of("message", "模型配置已删除"));
    }
}
