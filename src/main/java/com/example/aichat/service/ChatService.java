package com.example.aichat.service;

import com.example.aichat.config.DeepSeekConfig;
import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.model.Prompt;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ChatService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DeepSeekConfig deepSeekConfig;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private PromptService promptService;  // 用于获取提示词内容

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送消息，携带可选的提示词ID
     * @param conversationId 会话ID
     * @param userMessage 用户消息
     * @param promptId 提示词ID（可为null）
     * @return AI回复
     */
    public String chatAndSave(Long conversationId, String userMessage, Long promptId) {
        // 1. 获取该会话的历史消息（正序，最多30条）
        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(conversationId);
        if (history.size() > 30) {
            history = history.subList(history.size() - 30, history.size());
        }

        // 2. 构建 messages 数组
        ArrayNode messagesArray = objectMapper.createArrayNode();

        // 2a. 如果指定了提示词，先插入 system 角色消息
        if (promptId != null) {
            try {
                Prompt prompt = promptService.getPromptById(promptId);
                ObjectNode systemNode = messagesArray.addObject();
                systemNode.put("role", "system");
                systemNode.put("content", prompt.getContent());
            } catch (Exception e) {
                // 提示词不存在或无权访问时忽略，不影响正常对话
                System.err.println("提示词加载失败: " + e.getMessage());
            }
        }

        // 2b. 添加历史消息（user + assistant 成对）
        for (ChatMessage msg : history) {
            ObjectNode userNode = messagesArray.addObject();
            userNode.put("role", "user");
            userNode.put("content", msg.getUserMessage());

            ObjectNode assistantNode = messagesArray.addObject();
            assistantNode.put("role", "assistant");
            assistantNode.put("content", msg.getAiReply());
        }

        // 2c. 添加当前用户消息
        ObjectNode currentUserNode = messagesArray.addObject();
        currentUserNode.put("role", "user");
        currentUserNode.put("content", userMessage);

        // 3. 调用AI API
        String reply = callDeepSeek(messagesArray);

        // 4. 保存消息到数据库
        chatHistoryService.saveMessage(conversationId, userMessage, reply);

        // 5. 更新会话标题（您已实现的方法）
        updateConversationTitleIfNeeded(conversationId, userMessage);

        return reply;
    }

    /**
     * 如果会话标题是默认的"新对话"，则用用户消息的前15字更新
     */
    private void updateConversationTitleIfNeeded(Long conversationId, String userMessage) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);
        if (conversation == null) return;

        String defaultTitle = "新对话";
        if (conversation.getTitle() == null || conversation.getTitle().equals(defaultTitle)) {
            int maxLen = 15;
            String newTitle = userMessage.length() > maxLen
                    ? userMessage.substring(0, maxLen) + "…"
                    : userMessage;
            conversation.setTitle(newTitle);
            conversationRepository.save(conversation);
        }
    }

    /**
     * 调用DeepSeek API
     */
    private String callDeepSeek(ArrayNode messages) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "deepseek-chat");
        requestBody.set("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepSeekConfig.getApiKey());

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    deepSeekConfig.getApiUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            e.printStackTrace();
            return "AI回复失败：" + e.getMessage();
        }
    }
}


