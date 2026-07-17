package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.dto.AuthResponse;
import com.example.aichat.dto.LoginRequest;
import com.example.aichat.dto.RegisterRequest;
import com.example.aichat.model.User;
import com.example.aichat.repository.UserRepository;
import com.example.aichat.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    /**
     * 对邮箱地址进行脱敏处理，仅保留首字符和域名。
     * 例: "user@example.com" -> "u***@example.com"
     */
    private String maskEmail(String input) {
        if (input == null || !input.contains("@")) return input;
        int atIndex = input.indexOf('@');
        String localPart = input.substring(0, atIndex);
        String domain = input.substring(atIndex);
        if (localPart.length() <= 1) return localPart + "***" + domain;
        return localPart.charAt(0) + "***" + domain;
    }

    private String generatePid() {
        int pid = 100000 + ThreadLocalRandom.current().nextInt(900000);
        while (userRepository.existsByPid(String.valueOf(pid))) {
            pid = 100000 + ThreadLocalRandom.current().nextInt(900000);
        }
        return String.valueOf(pid);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw BusinessException.conflict("用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.conflict("该邮箱已注册");
        }

        emailService.verifyCode(request.getEmail(), request.getCode());
        validatePasswordStrength(request.getPassword());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .pid(generatePid())
                .balance(BigDecimal.valueOf(3)) // 新用户赠送3元
                .build();
        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setRole("USER");
        response.setUserId(user.getId());
        response.setBalance(user.getBalance());
        return response;
    }

    public AuthResponse login(LoginRequest request) {
        User user = null;
        String loginKey = request.getUsername();
        
        // 支持用户名或邮箱登录
        if (loginKey != null && !loginKey.isEmpty()) {
            user = userRepository.findByUsername(loginKey).orElse(null);
        }
        if (user == null && loginKey != null && !loginKey.isEmpty()) {
            user = userRepository.findByEmail(loginKey).orElse(null);
        }
        
        // 也支持email字段（前端可能分开传）
        if (user == null && request.getEmail() != null && !request.getEmail().isEmpty()) {
            user = userRepository.findByEmail(request.getEmail()).orElse(null);
        }

        if (user == null) {
            logger.warn("登录失败：用户不存在 - {}", maskEmail(loginKey));
            throw BusinessException.badRequest("用户名/邮箱或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("登录失败：密码错误 - {}", maskEmail(loginKey));
            throw BusinessException.badRequest("用户名/邮箱或密码错误");
        }

        if (!user.getEnabled()) {
            logger.warn("登录失败：账号已禁用 - {}", user.getUsername());
            throw BusinessException.badRequest("账号已被禁用，请联系管理员");
        }

        logger.info("登录成功 - {}", user.getUsername());
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setUserId(user.getId());
        return response;
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw BusinessException.badRequest("当前密码错误");
        }

        validatePasswordStrength(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw BusinessException.badRequest("密码长度至少8位");
        }
        if (!password.matches(".*[a-z].*")) {
            throw BusinessException.badRequest("密码需包含小写字母");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw BusinessException.badRequest("密码需包含大写字母");
        }
        if (!password.matches(".*\\d.*")) {
            throw BusinessException.badRequest("密码需包含数字");
        }
    }

    public void verifyPassword(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw BusinessException.badRequest("密码错误");
        }
    }

    public void updateSignature(Long userId, String signature) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        user.setSignature(signature);
        userRepository.save(user);
    }

    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
    }

    public void sendResetCode(String usernameOrEmail) {
        User user = null;
        logger.info("尝试通过用户名查找用户: {}", maskEmail(usernameOrEmail));
        if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
            user = userRepository.findByUsernameIgnoreCase(usernameOrEmail).orElse(null);
        }
        if (user != null) {
            logger.info("通过用户名找到用户: {}", user.getUsername());
        } else {
            logger.info("通过用户名未找到用户，尝试通过邮箱查找: {}", maskEmail(usernameOrEmail));
            if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
                user = userRepository.findByEmailIgnoreCase(usernameOrEmail).orElse(null);
            }
            if (user != null) {
                logger.info("通过邮箱找到用户: {}", user.getUsername());
            }
        }
        // 无论用户是否存在统一返回，防止用户枚举攻击
        if (user == null) {
            logger.warn("密码重置请求：用户不存在 - {}", maskEmail(usernameOrEmail));
            return; // 静默返回，不暴露用户不存在
        }
        emailService.sendResetCode(user.getEmail());
    }

    public void resetPassword(String usernameOrEmail, String code, String newPassword) {
        User user = null;
        logger.info("重置密码：尝试通过用户名查找: {}", maskEmail(usernameOrEmail));
        if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
            user = userRepository.findByUsernameIgnoreCase(usernameOrEmail).orElse(null);
        }
        if (user != null) {
            logger.info("重置密码：通过用户名找到用户: {}", user.getUsername());
        } else {
            logger.info("重置密码：通过用户名未找到，尝试通过邮箱查找: {}", maskEmail(usernameOrEmail));
            if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
                user = userRepository.findByEmailIgnoreCase(usernameOrEmail).orElse(null);
            }
            if (user != null) {
                logger.info("重置密码：通过邮箱找到用户: {}", user.getUsername());
            }
        }
        if (user == null) {
            logger.warn("重置密码：用户不存在: {}", maskEmail(usernameOrEmail));
            throw BusinessException.notFound("用户不存在");
        }

        emailService.verifyResetCode(user.getEmail(), code);
        validatePasswordStrength(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("密码重置成功 - {}", user.getUsername());
    }
}
