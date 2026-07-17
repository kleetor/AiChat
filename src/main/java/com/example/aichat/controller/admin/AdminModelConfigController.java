package com.example.aichat.controller.admin;

import com.example.aichat.dto.ModelConfigDTO;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.service.AdminAuditLogService;
import com.example.aichat.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AdminAuditLogService auditLogService;

    public AdminModelConfigController(AdminService adminService,
                                       AdminAuditLogService auditLogService) {
        this.adminService = adminService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<ModelConfigDTO>> getAll() {
        return ResponseEntity.ok(
                adminService.getModelConfigs().stream()
                        .map(ModelConfigDTO::from)
                        .collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<ModelConfig> create(@Valid @RequestBody ModelConfig config,
                                               HttpServletRequest request) {
        ModelConfig created = adminService.createModelConfig(config);
        // 记录审计日志
        Long adminId = getAdminIdFromRequest(request);
        auditLogService.logModelCreate(adminId, auditLogService.resolveAdminUsername(adminId), created.getId(),
                created.getModelName(), request.getRemoteAddr());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelConfig> update(@PathVariable Long id, @Valid @RequestBody ModelConfig config,
                                               HttpServletRequest request) {
        ModelConfig updated = adminService.updateModelConfig(id, config);
        // 记录审计日志
        Long adminId = getAdminIdFromRequest(request);
        auditLogService.logModelUpdate(adminId, auditLogService.resolveAdminUsername(adminId), updated.getId(),
                updated.getModelName(), request.getRemoteAddr());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        adminService.deleteModelConfig(id);
        // 记录审计日志
        Long adminId = getAdminIdFromRequest(request);
        auditLogService.logModelDelete(adminId, auditLogService.resolveAdminUsername(adminId), id,
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "模型配置已删除"));
    }

    private Long getAdminIdFromRequest(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
