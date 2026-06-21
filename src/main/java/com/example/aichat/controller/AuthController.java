package com.example.aichat.controller;

import com.example.aichat.dto.AuthResponse;
import com.example.aichat.dto.LoginRequest;
import com.example.aichat.dto.RegisterRequest;
import com.example.aichat.model.User;
import com.example.aichat.repository.FriendshipRepository;
import com.example.aichat.repository.PromptsHubRepository;
import com.example.aichat.service.EmailService;
import com.example.aichat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${upload.user-pic-dir:./uploads/userPic}")
    private String userPicDir;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PromptsHubRepository promptsHubRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @PostMapping("/send-code")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱不能为空"));
        }
        try {
            emailService.sendVerificationCode(email);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = userService.register(request);
            return ResponseEntity.ok(Map.of(
                    "token", response.getToken(),
                    "username", response.getUsername(),
                    "balance", response.getBalance()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(Map.of(
                    "token", response.getToken(),
                    "username", response.getUsername()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getUserInfo(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body("未授权");
        }
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "email", user.getEmail(),
                "pid", user.getPid(),
                "signature", user.getSignature() != null ? user.getSignature() : "",
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "balance", user.getBalance()
        ));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserCard(@PathVariable Long userId,
                                         Authentication auth) {
        User user = userService.getUserById(userId);
        long shareCount = promptsHubRepository.countByUserId(userId);
        int totalLikes = promptsHubRepository.sumLikesByUserId(userId);
        // 检查好友状态
        String friendStatus = "NONE";
        if (auth != null && auth.getPrincipal() != null) {
            Long currentUserId = (Long) auth.getPrincipal();
            if (!currentUserId.equals(userId)) {
                if (friendshipRepository.existsActiveRelation(currentUserId, userId)) {
                    // 进一步判断是 ACCEPTED 还是 PENDING
                    var fsOpt1 = friendshipRepository.findByUserIdAndFriendId(currentUserId, userId);
                    var fsOpt2 = friendshipRepository.findByUserIdAndFriendId(userId, currentUserId);
                    var fs = fsOpt1.isPresent() ? fsOpt1.get() : fsOpt2.orElse(null);
                    if (fs != null) {
                        friendStatus = fs.getStatus();
                    }
                }
            }
        }
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "pid", user.getPid(),
                "signature", user.getSignature() != null ? user.getSignature() : "",
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "shareCount", shareCount,
                "totalLikes", totalLikes,
                "friendStatus", friendStatus
        ));
    }

    @PostMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> request,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String signature = request.get("signature");
        if (signature != null && signature.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("message", "签名长度不能超过200个字符"));
        }
        userService.updateSignature(userId, signature);
        return ResponseEntity.ok(Map.of("message", "更新成功"));
    }

    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请选择图片"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "只允许上传图片"));
        }
        try {
            String resolvedDir = userPicDir;
            if (!new File(userPicDir).isAbsolute()) {
                resolvedDir = System.getProperty("user.dir") + File.separator + userPicDir;
            }
            File dirFile = new File(resolvedDir);
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            String ext = ".png";
            String originalName = file.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String fileName = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            File target = new File(dirFile, fileName);
            file.transferTo(target);
            String avatarUrl = "/uploads/userPic/" + fileName;
            userService.updateAvatar(userId, avatarUrl);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "上传失败"));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");
        try {
            userService.changePassword(userId, currentPassword, newPassword);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestBody Map<String, String> request,
                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String password = request.get("password");
        try {
            userService.verifyPassword(userId, password);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/send-reset-code")
    public ResponseEntity<Map<String, Object>> sendResetCode(@RequestBody Map<String, String> request) {
        String usernameOrEmail = request.get("username");
        if (usernameOrEmail == null || usernameOrEmail.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "请输入用户名或邮箱");
            return ResponseEntity.badRequest().body(response);
        }
        try {
            userService.sendResetCode(usernameOrEmail);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        String usernameOrEmail = request.get("username");
        String code = request.get("code");
        String newPassword = request.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "新密码长度至少6位");
            return ResponseEntity.badRequest().body(response);
        }
        try {
            userService.resetPassword(usernameOrEmail, code, newPassword);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}