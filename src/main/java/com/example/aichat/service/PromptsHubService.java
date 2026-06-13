package com.example.aichat.service;

import com.example.aichat.model.PromptsHub;
import com.example.aichat.model.User;
import com.example.aichat.repository.PromptsHubRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PromptsHubService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );

    @Autowired
    private PromptsHubRepository promptsHubRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${upload.dir:./uploads/images}")
    private String uploadDir;

    @Value("${upload.url-prefix:/uploads/images}")
    private String uploadUrlPrefix;

    public List<PromptsHub> getAllPrompts() {
        return promptsHubRepository.findAllByOrderByLikesCountDescCreatedAtDesc();
    }

    public PromptsHub getPromptById(Long id) {
        return promptsHubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("提示词不存在"));
    }

    public PromptsHub uploadPrompt(Long userId, String name, String content, String userMessage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        PromptsHub prompt = PromptsHub.builder()
                .name(name)
                .content(content)
                .userId(userId)
                .userName(user.getUsername())
                .userMessage(userMessage)
                .build();

        return promptsHubRepository.save(prompt);
    }

    @Transactional
    public PromptsHub uploadPromptWithImage(Long userId, String name, String content,
                                             String userMessage, MultipartFile image) {
        PromptsHub prompt = uploadPrompt(userId, name, content, userMessage);
        if (image != null && !image.isEmpty()) {
            String imageUrl = saveImage(image);
            prompt.setImageUrl(imageUrl);
            promptsHubRepository.save(prompt);
        }
        return prompt;
    }

    @Transactional
    public void likePrompt(Long id) {
        if (!promptsHubRepository.existsById(id)) {
            throw new RuntimeException("提示词不存在");
        }
        promptsHubRepository.incrementLikes(id);
    }

    public List<PromptsHub> getUserUploadedPrompts(Long userId) {
        return promptsHubRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public PromptsHub updateImageUrl(Long id, MultipartFile image) {
        PromptsHub prompt = promptsHubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("提示词不存在"));
        if (image != null && !image.isEmpty()) {
            String imageUrl = saveImage(image);
            prompt.setImageUrl(imageUrl);
            promptsHubRepository.save(prompt);
        }
        return prompt;
    }

    private String saveImage(MultipartFile image) {
        if (image.getContentType() == null || !ALLOWED_IMAGE_TYPES.contains(image.getContentType().toLowerCase())) {
            throw new IllegalArgumentException("仅支持 PNG / JPG / GIF / WEBP 图片");
        }
        String resolvedDir = uploadDir;
        if (!new File(uploadDir).isAbsolute()) {
            resolvedDir = System.getProperty("user.dir") + File.separator + uploadDir;
        }
        File dir = new File(resolvedDir);
        try {
            resolvedDir = dir.getCanonicalPath();
            dir = new File(resolvedDir);
        } catch (java.io.IOException ignored) {
            // fallback to absolute path
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("无法创建上传目录: " + dir.getAbsolutePath());
        }

        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID() + extension.toLowerCase();
        File target = new File(dir, newFilename);

        try {
            image.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("图片保存失败: " + e.getMessage(), e);
        }

        String prefix = uploadUrlPrefix.endsWith("/") ? uploadUrlPrefix : uploadUrlPrefix + "/";
        return prefix + newFilename;
    }
}
