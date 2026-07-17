package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.PromptsHub;
import com.example.aichat.repository.PromptsHubRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPromptService {

    private final PromptsHubRepository promptsHubRepository;
    private final NotificationService notificationService;

    public AdminPromptService(PromptsHubRepository promptsHubRepository, NotificationService notificationService) {
        this.promptsHubRepository = promptsHubRepository;
        this.notificationService = notificationService;
    }

    public Page<PromptsHub> getPromptsHub(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (keyword != null && !keyword.isEmpty()) {
            return promptsHubRepository.searchByKeyword(keyword, pageable);
        }
        return promptsHubRepository.findByStatusNot("removed", pageable);
    }

    public void deletePromptHub(Long id) {
        promptsHubRepository.deleteById(id);
    }

    @Transactional
    public PromptsHub setFeatured(Long id, Boolean featured) {
        PromptsHub prompt = promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        prompt.setFeatured(featured);
        return promptsHubRepository.save(prompt);
    }

    public Page<PromptsHub> getAuditQueue(String status, int page, int size) {
        return promptsHubRepository.findByStatusOrderByCreatedAtAsc(
                status, PageRequest.of(page, size));
    }

    @Transactional
    public void approvePrompt(Long id) {
        PromptsHub p = promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (!"pending_review".equals(p.getStatus())) {
            throw BusinessException.conflict("当前状态不允许审核");
        }
        p.setStatus("published");
        promptsHubRepository.save(p);
        notificationService.create(null, "系统",
                p.getUserId(), "PROMPT_APPROVED",
                "你的提示词已通过审核",
                p.getName(), id, null);
    }

    @Transactional
    public void rejectPrompt(Long id, String reason) {
        PromptsHub p = promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        if (!"pending_review".equals(p.getStatus())) {
            throw BusinessException.conflict("当前状态不允许审核");
        }
        p.setStatus("rejected");
        promptsHubRepository.save(p);
        String reasonText = reason.isBlank() ? "" : " 原因：" + reason;
        notificationService.create(null, "系统",
                p.getUserId(), "PROMPT_REJECTED",
                "你的提示词「" + p.getName() + "」未通过审核",
                reason.isBlank() ? "未提供原因" : reason, id, null);
    }

    @Transactional
    public void unpublishPrompt(Long id) {
        PromptsHub p = promptsHubRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("提示词不存在"));
        p.setStatus("removed");
        promptsHubRepository.save(p);
        notificationService.create(null, "系统",
                p.getUserId(), "PROMPT_REMOVED",
                "你的提示词「" + p.getName() + "」已被管理员下架",
                "如有疑问请联系管理员", id, null);
    }
}
