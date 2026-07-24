// service/PromptService.java
package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.Prompt;
import com.example.aichat.model.User;
import com.example.aichat.repository.PromptRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptService {

    @Autowired
    private PromptRepository promptRepository;

    @Autowired
    private UserRepository userRepository;

    @Cacheable(value = "promptsByUser", key = "#userId")
    public List<Prompt> getUserPrompts(Long userId) {
        return promptRepository.findByUserIdOrderByIdDesc(userId);
    }

    @CacheEvict(value = "promptsByUser", key = "#userId")
    public Prompt createPrompt(Long userId, String name, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        Prompt prompt = Prompt.builder()
                .user(user)
                .name(name)
                .content(content)
                .build();
        return promptRepository.save(prompt);
    }

    @Caching(evict = {
        @CacheEvict(value = "promptsByUser", key = "#userId"),
        @CacheEvict(value = "promptsByUser", key = "#promptId")
    })
    public Prompt updatePrompt(Long promptId, Long userId, String name, String content) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (!prompt.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("无权修改");
        }
        prompt.setName(name);
        prompt.setContent(content);
        return promptRepository.save(prompt);
    }

    @Caching(evict = {
        @CacheEvict(value = "promptsByUser", key = "#userId"),
        @CacheEvict(value = "promptsByUser", key = "#promptId")
    })
    public void deletePrompt(Long promptId, Long userId) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (!prompt.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("无权删除");
        }
        promptRepository.delete(prompt);
    }

    @Cacheable(value = "promptsByUser", key = "#promptId")
    public Prompt getPromptById(Long promptId) {
        return promptRepository.findById(promptId)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
    }

    public Prompt getPromptByIdAndUser(Long promptId, Long userId) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (!prompt.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("无权访问此提示词");
        }
        return prompt;
    }
}
