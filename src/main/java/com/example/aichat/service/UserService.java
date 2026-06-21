package com.example.aichat.service;

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
import java.util.Random;

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

    private final Random random = new Random();

    private String generatePid() {
        int pid = 100000 + random.nextInt(900000);
        while (userRepository.existsByPid(String.valueOf(pid))) {
            pid = 100000 + random.nextInt(900000);
        }
        return String.valueOf(pid);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("该邮箱已注册");
        }

        emailService.verifyCode(request.getEmail(), request.getCode());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .pid(generatePid())
                .balance(BigDecimal.ONE) // 新用户赠送1元
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
            logger.warn("登录失败：用户不存在 - {}", loginKey);
            throw new RuntimeException("用户名/邮箱或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("登录失败：密码错误 - {}", loginKey);
            throw new RuntimeException("用户名/邮箱或密码错误");
        }

        if (!user.getEnabled()) {
            logger.warn("登录失败：账号已禁用 - {}", user.getUsername());
            throw new RuntimeException("账号已被禁用，请联系管理员");
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
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("当前密码错误");
        }

        if (newPassword.length() < 6) {
            throw new RuntimeException("新密码长度至少6位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void verifyPassword(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
    }

    public void updateSignature(Long userId, String signature) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setSignature(signature);
        userRepository.save(user);
    }

    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
    }

    public void sendResetCode(String usernameOrEmail) {
        User user = null;
        logger.info("尝试通过用户名查找用户: {}", usernameOrEmail);
        if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
            user = userRepository.findByUsernameIgnoreCase(usernameOrEmail).orElse(null);
        }
        if (user != null) {
            logger.info("通过用户名找到用户: {}", user.getUsername());
        } else {
            logger.info("通过用户名未找到用户，尝试通过邮箱查找: {}", usernameOrEmail);
            if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
                user = userRepository.findByEmailIgnoreCase(usernameOrEmail).orElse(null);
            }
            if (user != null) {
                logger.info("通过邮箱找到用户: {}", user.getUsername());
            }
        }
        if (user == null) {
            logger.warn("用户不存在: {}", usernameOrEmail);
            throw new RuntimeException("用户不存在");
        }
        emailService.sendResetCode(user.getEmail());
    }

    public void resetPassword(String usernameOrEmail, String code, String newPassword) {
        User user = null;
        logger.info("重置密码：尝试通过用户名查找: {}", usernameOrEmail);
        if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
            user = userRepository.findByUsernameIgnoreCase(usernameOrEmail).orElse(null);
        }
        if (user != null) {
            logger.info("重置密码：通过用户名找到用户: {}", user.getUsername());
        } else {
            logger.info("重置密码：通过用户名未找到，尝试通过邮箱查找: {}", usernameOrEmail);
            if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
                user = userRepository.findByEmailIgnoreCase(usernameOrEmail).orElse(null);
            }
            if (user != null) {
                logger.info("重置密码：通过邮箱找到用户: {}", user.getUsername());
            }
        }
        if (user == null) {
            logger.warn("重置密码：用户不存在: {}", usernameOrEmail);
            throw new RuntimeException("用户不存在");
        }

        emailService.verifyResetCode(user.getEmail(), code);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("密码重置成功 - {}", user.getUsername());
    }
}
