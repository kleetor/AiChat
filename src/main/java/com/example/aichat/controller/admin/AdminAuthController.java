package com.example.aichat.controller.admin;

import com.example.aichat.dto.AuthResponse;
import com.example.aichat.dto.LoginRequest;
import com.example.aichat.model.User;
import com.example.aichat.repository.UserRepository;
import com.example.aichat.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        if (!"ADMIN".equals(user.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "无管理员权限"));
        }

        if (!user.getEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "账号已被禁用"));
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), "ADMIN");
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setRole("ADMIN");
        response.setUserId(user.getId());
        return ResponseEntity.ok(response);
    }
}
