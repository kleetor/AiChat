package com.example.aichat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private final Map<String, EmailCode> codeMap = new ConcurrentHashMap<>();
    private final Map<String, EmailCode> resetCodeMap = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static class EmailCode {
        String code;
        long createTime;
        int verifyCount;
        boolean used;

        EmailCode(String code) {
            this.code = code;
            this.createTime = System.currentTimeMillis();
            this.verifyCount = 0;
            this.used = false;
        }
    }

    public void sendVerificationCode(String email) {
        if (codeMap.containsKey(email)) {
            EmailCode existing = codeMap.get(email);
            if (System.currentTimeMillis() - existing.createTime < 60000) {
                throw new RuntimeException("请稍后再试，验证码已发送");
            }
        }

        String code = String.format("%06d", random.nextInt(1000000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("AI Chat 验证码");
        message.setText("您的验证码是：" + code + "，5分钟内有效。");

        try {
            mailSender.send(message);
            codeMap.put(email, new EmailCode(code));
            logger.info("验证码已发送至: {}", email);
        } catch (Exception e) {
            logger.error("发送验证码失败: {}", email, e);
            throw new RuntimeException("发送验证码失败，请检查邮箱地址");
        }
    }

    public void verifyCode(String email, String code) {
        EmailCode emailCode = codeMap.get(email);
        if (emailCode == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }

        if (emailCode.used) {
            throw new RuntimeException("验证码已被使用");
        }

        if (System.currentTimeMillis() - emailCode.createTime > 300000) {
            codeMap.remove(email);
            throw new RuntimeException("验证码已过期");
        }

        if (!emailCode.code.equals(code)) {
            emailCode.verifyCount++;
            if (emailCode.verifyCount >= 5) {
                codeMap.remove(email);
                throw new RuntimeException("验证码错误次数过多，请重新获取");
            }
            throw new RuntimeException("验证码错误");
        }

        emailCode.used = true;
    }

    public void sendResetCode(String email) {
        if (resetCodeMap.containsKey(email)) {
            EmailCode existing = resetCodeMap.get(email);
            if (System.currentTimeMillis() - existing.createTime < 60000) {
                throw new RuntimeException("请稍后再试，验证码已发送");
            }
        }

        String code = String.format("%06d", random.nextInt(1000000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(email);
        message.setSubject("AI Chat 密码重置验证码");
        message.setText("您的密码重置验证码是：" + code + "，5分钟内有效。");

        try {
            mailSender.send(message);
            resetCodeMap.put(email, new EmailCode(code));
            logger.info("密码重置验证码已发送至: {}", email);
        } catch (Exception e) {
            logger.error("发送密码重置验证码失败: {}", email, e);
            throw new RuntimeException("发送验证码失败，请检查邮箱地址");
        }
    }

    public void verifyResetCode(String email, String code) {
        EmailCode emailCode = resetCodeMap.get(email);
        if (emailCode == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }

        if (emailCode.used) {
            throw new RuntimeException("验证码已被使用");
        }

        if (System.currentTimeMillis() - emailCode.createTime > 300000) {
            resetCodeMap.remove(email);
            throw new RuntimeException("验证码已过期");
        }

        if (!emailCode.code.equals(code)) {
            emailCode.verifyCount++;
            if (emailCode.verifyCount >= 5) {
                resetCodeMap.remove(email);
                throw new RuntimeException("验证码错误次数过多，请重新获取");
            }
            throw new RuntimeException("验证码错误");
        }

        emailCode.used = true;
    }

    @Scheduled(fixedRate = 60000)
    public void cleanExpiredCodes() {
        long now = System.currentTimeMillis();
        codeMap.entrySet().removeIf(entry ->
                now - entry.getValue().createTime > 300000);
        resetCodeMap.entrySet().removeIf(entry ->
                now - entry.getValue().createTime > 300000);
    }
}
