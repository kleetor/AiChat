package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.config.props.MailProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    private final MailProperties mailProperties;

    public EmailService(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    private final Map<String, EmailCode> codeMap = new ConcurrentHashMap<>();
    private final Map<String, EmailCode> resetCodeMap = new ConcurrentHashMap<>();

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

    private String generateCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    public void sendVerificationCode(String email) {
        String code = generateCode();

        // 原子地检查并预留验证码槽位，防止并发 TOCTOU 绕过
        codeMap.compute(email, (k, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.createTime < 60000) {
                throw BusinessException.badRequest("请稍后再试，验证码已发送");
            }
            return new EmailCode(code);
        });

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getUsername());
        message.setTo(email);
        message.setSubject("AI Chat 验证码");
        message.setText("您的验证码是：" + code + "，5分钟内有效。");

        try {
            mailSender.send(message);
            logger.info("验证码已发送至: {}", email);
        } catch (Exception e) {
            codeMap.remove(email); // 发送失败时回滚，允许重试
            logger.error("发送验证码失败: {}", email, e);
            throw new RuntimeException("发送验证码失败，请检查邮箱地址");
        }
    }

    public void verifyCode(String email, String code) {
        EmailCode emailCode = codeMap.get(email);
        if (emailCode == null) {
            throw BusinessException.badRequest("验证码不存在或已过期");
        }

        if (emailCode.used) {
            throw BusinessException.badRequest("验证码已被使用");
        }

        if (System.currentTimeMillis() - emailCode.createTime > 300000) {
            codeMap.remove(email);
            throw BusinessException.badRequest("验证码已过期");
        }

        if (!emailCode.code.equals(code)) {
            emailCode.verifyCount++;
            if (emailCode.verifyCount >= 5) {
                codeMap.remove(email);
                throw BusinessException.badRequest("验证码错误次数过多，请重新获取");
            }
            throw BusinessException.badRequest("验证码错误");
        }

        emailCode.used = true;
    }

    public void sendResetCode(String email) {
        String code = generateCode();

        // 原子地检查并预留验证码槽位，防止并发 TOCTOU 绕过
        resetCodeMap.compute(email, (k, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.createTime < 60000) {
                throw BusinessException.badRequest("请稍后再试，验证码已发送");
            }
            return new EmailCode(code);
        });

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getUsername());
        message.setTo(email);
        message.setSubject("AI Chat 密码重置验证码");
        message.setText("您的密码重置验证码是：" + code + "，5分钟内有效。");

        try {
            mailSender.send(message);
            logger.info("密码重置验证码已发送至: {}", email);
        } catch (Exception e) {
            resetCodeMap.remove(email); // 发送失败时回滚，允许重试
            logger.error("发送密码重置验证码失败: {}", email, e);
            throw new RuntimeException("发送验证码失败，请检查邮箱地址");
        }
    }

    public void verifyResetCode(String email, String code) {
        EmailCode emailCode = resetCodeMap.get(email);
        if (emailCode == null) {
            throw BusinessException.badRequest("验证码不存在或已过期");
        }

        if (emailCode.used) {
            throw BusinessException.badRequest("验证码已被使用");
        }

        if (System.currentTimeMillis() - emailCode.createTime > 300000) {
            resetCodeMap.remove(email);
            throw BusinessException.badRequest("验证码已过期");
        }

        if (!emailCode.code.equals(code)) {
            emailCode.verifyCount++;
            if (emailCode.verifyCount >= 5) {
                resetCodeMap.remove(email);
                throw BusinessException.badRequest("验证码错误次数过多，请重新获取");
            }
            throw BusinessException.badRequest("验证码错误");
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
