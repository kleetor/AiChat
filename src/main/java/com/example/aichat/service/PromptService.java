// service/PromptService.java
package com.example.aichat.service;

import com.example.aichat.model.Prompt;
import com.example.aichat.model.User;
import com.example.aichat.repository.PromptRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptService {

    @Autowired
    private PromptRepository promptRepository;

    @Autowired
    private UserRepository userRepository;

    public List<Prompt> getUserPrompts(Long userId) {
        return promptRepository.findByUserIdOrderByIdDesc(userId);
    }

    public Prompt createPrompt(Long userId, String name, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Prompt prompt = Prompt.builder()
                .user(user)
                .name(name)
                .content(content)
                .build();
        return promptRepository.save(prompt);
    }

    public Prompt updatePrompt(Long promptId, Long userId, String name, String content) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new RuntimeException("提示词不存在"));
        if (!prompt.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权修改");
        }
        prompt.setName(name);
        prompt.setContent(content);
        return promptRepository.save(prompt);
    }

    public void deletePrompt(Long promptId, Long userId) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new RuntimeException("提示词不存在"));
        if (!prompt.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权删除");
        }
        promptRepository.delete(prompt);
    }

    public Prompt getPromptById(Long promptId) {
        return promptRepository.findById(promptId)
                .orElseThrow(() -> new RuntimeException("提示词不存在"));
    }
}
