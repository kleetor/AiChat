package com.example.aichat.controller.admin;

import com.example.aichat.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminService adminService;

    public AdminDashboardController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/dashboard/charts")
    public ResponseEntity<Map<String, Object>> charts(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "7")
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(365) int days) {
        return ResponseEntity.ok(adminService.getChartData(days));
    }
}
