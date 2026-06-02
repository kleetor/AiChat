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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

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

    @Autowired
    private SearchService searchService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String chatAndSave(Long conversationId, String userMessage, Long promptId, Long modelConfigId) {
        return chatAndSave(conversationId, userMessage, promptId, modelConfigId, false);
    }

    public String chatAndSave(Long conversationId, String userMessage, Long promptId, Long modelConfigId, boolean webSearchEnabled) {
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

        if (webSearchEnabled) {
            try {
                String searchResults = searchService.searchAsMarkdown(userMessage, 5);
                ObjectNode searchContextNode = messagesArray.addObject();
                searchContextNode.put("role", "system");
                searchContextNode.put("content", "最新搜索信息：\n" + searchResults);
            } catch (Exception e) {
                System.err.println("联网搜索失败: " + e.getMessage());
            }
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
     * 流式聊天：使用 Spring 官方 SseEmitter，自动处理缓冲、心跳、客户端断开等
     */
    public SseEmitter chatStream(Long conversationId, String userMessage, Long promptId, Long modelConfigId) {
        return chatStream(conversationId, userMessage, promptId, modelConfigId, false);
    }

    public SseEmitter chatStream(Long conversationId, String userMessage, Long promptId, Long modelConfigId, boolean webSearchEnabled) {
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

        if (webSearchEnabled) {
            try {
                String searchResults = searchService.searchAsMarkdown(userMessage, 5);
                ObjectNode searchContextNode = messagesArray.addObject();
                searchContextNode.put("role", "system");
                searchContextNode.put("content", "最新搜索信息：\n" + searchResults);
            } catch (Exception e) {
                System.err.println("联网搜索失败: " + e.getMessage());
            }
        }

        ObjectNode currentUserNode = messagesArray.addObject();
        currentUserNode.put("role", "user");
        currentUserNode.put("content", userMessage);

        return streamDeepSeek(messagesArray, config, conversationId, userMessage);
    }

    private SseEmitter streamDeepSeek(ArrayNode messages, ModelConfig config, Long conversationId, String userMessage) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String apiUrl = config.getApiUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();

        Runnable task = () -> {
            try {
                PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
                connectionManager.setMaxTotal(50);
                connectionManager.setDefaultMaxPerRoute(20);

                RequestConfig requestConfig = RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofSeconds(120))
                        .build();

                try (CloseableHttpClient httpClient = HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setDefaultRequestConfig(requestConfig)
                        .build()) {

                    ObjectNode requestBody = objectMapper.createObjectNode();
                    requestBody.put("model", modelName);
                    requestBody.put("stream", true);
                    requestBody.set("messages", messages);

                    HttpPost postRequest = new HttpPost(apiUrl);
                    postRequest.setHeader("Content-Type", "application/json");
                    postRequest.setHeader("Authorization", "Bearer " + apiKey);
                    postRequest.setEntity(new StringEntity(requestBody.toString(), StandardCharsets.UTF_8));

                    try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                        int statusCode = response.getCode();
                        if (statusCode >= 400) {
                            String errorMsg = "AI 服务返回错误: HTTP " + statusCode;
                            try {
                                if (response.getEntity() != null) {
                                    errorMsg += " - " + new String(
                                            response.getEntity().getContent().readAllBytes(),
                                            StandardCharsets.UTF_8);
                                }
                            } catch (Exception ignore) { }
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(errorMsg, MediaType.TEXT_PLAIN));
                            emitter.complete();
                            return;
                        }

                        if (response.getEntity() == null) {
                            emitter.send("AI 服务无返回内容");
                            emitter.complete();
                            return;
                        }

                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));

                        StringBuilder fullResponse = new StringBuilder();
                        StringBuilder chunkBuf = new StringBuilder();
                        String line;
                        int eventCount = 0;
                        final int flushEvery = 4;
                        final long sleepMs = 50;
                        int sinceLastFlush = 0;
                        while ((line = reader.readLine()) != null) {
                            if (line.trim().isEmpty()) {
                                continue;
                            }
                            try {
                                String payload = line;
                                if (payload.startsWith("data:")) {
                                    payload = payload.substring(5).trim();
                                }
                                if (payload.isEmpty()) {
                                    continue;
                                }
                                if ("[DONE]".equals(payload)) {
                                    break;
                                }

                                JsonNode root = objectMapper.readTree(payload);
                                JsonNode choices = root.get("choices");
                                if (choices != null && choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null && delta.has("content")
                                            && !delta.get("content").isNull()) {
                                        String content = delta.get("content").asText();
                                        if (content == null) continue;
                                        fullResponse.append(content);
                                        chunkBuf.append(content);
                                        sinceLastFlush += content.length();
                                        if (sinceLastFlush >= flushEvery
                                                || containsSentenceEnd(content)) {
                                            String toSend = chunkBuf.toString();
                                            chunkBuf.setLength(0);
                                            sinceLastFlush = 0;
                                            emitter.send(SseEmitter.event()
                                                    .id(String.valueOf(eventCount++))
                                                    .data(toSend, MediaType.TEXT_PLAIN));
                                            try {
                                                Thread.sleep(sleepMs);
                                            } catch (InterruptedException ie) {
                                                Thread.currentThread().interrupt();
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                        if (chunkBuf.length() > 0) {
                            try {
                                emitter.send(SseEmitter.event()
                                        .id(String.valueOf(eventCount++))
                                        .data(chunkBuf.toString(), MediaType.TEXT_PLAIN));
                            } catch (Exception ignore) {
                            }
                        }
                        reader.close();

                        String completeResponse = fullResponse.toString();
                        if (!completeResponse.isEmpty()) {
                            chatHistoryService.saveMessage(conversationId, userMessage, completeResponse);
                            updateConversationTitleIfNeeded(conversationId, userMessage);
                        }

                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("[DONE]", MediaType.TEXT_PLAIN));
                        emitter.complete();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("AI回复失败：" + e.getMessage(), MediaType.TEXT_PLAIN));
                } catch (Exception ignore) { }
                emitter.completeWithError(e);
            }
        };

        new Thread(task, "chat-stream-" + conversationId).start();
        return emitter;
    }

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

    private static boolean containsSentenceEnd(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '!' || c == '？' || c == '?'
                    || c == '…' || c == '\n' || c == '；' || c == ';') {
                return true;
            }
        }
        return false;
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