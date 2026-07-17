package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.dto.SystemRuleRequest;
import com.example.aichat.model.SystemRule;
import com.example.aichat.repository.SystemRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AdminSystemRuleService {

    private final SystemRuleRepository systemRuleRepository;

    public AdminSystemRuleService(SystemRuleRepository systemRuleRepository) {
        this.systemRuleRepository = systemRuleRepository;
    }

    public List<SystemRule> list() {
        return systemRuleRepository.findAll();
    }

    @Transactional
    public SystemRule createFromRequest(SystemRuleRequest req) {
        SystemRule rule = SystemRule.builder()
                .name(req.getName())
                .content(req.getContent())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
        return systemRuleRepository.save(rule);
    }

    @Transactional
    public SystemRule updateFromRequest(Long id, SystemRuleRequest req) {
        SystemRule rule = systemRuleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("规则不存在"));
        rule.setName(req.getName());
        rule.setContent(req.getContent());
        if (req.getIsActive() != null) rule.setIsActive(req.getIsActive());
        if (req.getSortOrder() != null) rule.setSortOrder(req.getSortOrder());
        rule.setUpdatedAt(LocalDateTime.now());
        return systemRuleRepository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        systemRuleRepository.deleteById(id);
    }

    @Transactional
    public SystemRule toggle(Long id) {
        SystemRule rule = systemRuleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("规则不存在"));
        rule.setIsActive(!rule.getIsActive());
        rule.setUpdatedAt(LocalDateTime.now());
        return systemRuleRepository.save(rule);
    }

    @Transactional
    public void updateSort(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            Long id = ((Number) item.get("id")).longValue();
            int order = (Integer) item.get("sortOrder");
            systemRuleRepository.findById(id).ifPresent(r -> {
                r.setSortOrder(order);
                systemRuleRepository.save(r);
            });
        }
    }
}
