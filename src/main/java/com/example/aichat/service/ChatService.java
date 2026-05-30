package com.example.aichat.service;

import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.model.Prompt;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.repository.ModelConfigRepository;
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
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private PromptService promptService;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送消息，必须指定模型配置ID
     * @param conversationId 会话ID
     * @param userMessage 用户消息
     * @param promptId 提示词ID（可为null）
     * @param modelConfigId 模型配置ID（必须指定，不能为null）
     * @return AI回复
     */
    public String chatAndSave(Long conversationId, String userMessage, Long promptId, Long modelConfigId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));

        if (modelConfigId == null) {
            throw new RuntimeException("请先选择模型配置");
        }

        ModelConfig config = modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));

        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(conversationId);
        if (history.size() > 30) {
            history = history.subList(history.size() - 30, history.size());
        }

        ArrayNode messagesArray = objectMapper.createArrayNode();

        if (promptId != null) {
            try {
                Prompt prompt = promptService.getPromptById(promptId);
                ObjectNode systemNode = messagesArray.addObject();
                systemNode.put("role", "system");
                systemNode.put("content", prompt.getContent());
            } catch (Exception e) {
                System.err.println("提示词加载失败: " + e.getMessage());
            }
        }

        for (ChatMessage msg : history) {
            ObjectNode userNode = messagesArray.addObject();
            userNode.put("role", "user");
            userNode.put("content", msg.getUserMessage());

            ObjectNode assistantNode = messagesArray.addObject();
            assistantNode.put("role", "assistant");
            assistantNode.put("content", msg.getAiReply());
        }

        ObjectNode currentUserNode = messagesArray.addObject();
        currentUserNode.put("role", "user");
        currentUserNode.put("content", userMessage);

        String reply = callDeepSeek(messagesArray, config);

        chatHistoryService.saveMessage(conversationId, userMessage, reply);

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

    private String callDeepSeek(ArrayNode messages, ModelConfig config) {
        String apiUrl = config.getApiUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", modelName);
        requestBody.set("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
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
