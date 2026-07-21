package com.example.aichat.service.tool;

import com.example.aichat.config.props.S3Properties;
import com.example.aichat.service.ImageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * analyze_image 工具实现 —— 复用 {@link ImageService#recognizeImage(String)} 调用视觉模型。
 */
@Component
public class AnalyzeImageTool implements ToolHandler {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeImageTool.class);

    private final ImageService imageService;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;

    public AnalyzeImageTool(ImageService imageService, S3Properties s3Properties,
                             ObjectMapper objectMapper) {
        this.imageService = imageService;
        this.s3Properties = s3Properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "analyze_image";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode paramsNode = objectMapper.createObjectNode();
        paramsNode.put("type", "object");

        ObjectNode propertiesNode = objectMapper.createObjectNode();
        ObjectNode urlProp = objectMapper.createObjectNode();
        urlProp.put("type", "string");
        urlProp.put("description", "图片的 URL 地址");
        propertiesNode.set("image_url", urlProp);
        paramsNode.set("properties", propertiesNode);

        ArrayNode required = objectMapper.createArrayNode();
        required.add("image_url");
        paramsNode.set("required", required);

        return new ToolDefinition(
                "analyze_image",
                "识别和分析用户上传的图片内容。当用户在对话中上传了图片并询问相关问题时，" +
                "应调用此工具获取图片的详细描述。可以识别图片中的物体、人物、文字、场景等。",
                paramsNode
        );
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String imageUrl;
        try {
            JsonNode args = objectMapper.readTree(call.getArguments());
            JsonNode urlNode = args.get("image_url");
            if (urlNode == null || urlNode.asText().isBlank()) {
                return new ToolResult(call.getId(), name(), "图片识别失败：未提供图片 URL");
            }
            imageUrl = urlNode.asText();
        } catch (JsonProcessingException e) {
            logger.warn("解析 analyze_image arguments 失败: {}", call.getArguments());
            return new ToolResult(call.getId(), name(), "图片识别失败：参数解析错误");
        }

        // URL 白名单校验 —— 仅允许已知 S3 存储域名
        if (!isAllowedImageUrl(imageUrl)) {
            logger.warn("analyze_image 收到非允许域名的 URL: {}", imageUrl);
            return new ToolResult(call.getId(), name(),
                    "图片识别失败：不允许的图片来源。仅支持已上传到系统的图片。");
        }

        logger.info("analyze_image url={}", imageUrl);

        try {
            String description = imageService.recognizeImage(imageUrl);
            String formatted = imageService.formatImageDescription(imageUrl, description);
            return new ToolResult(call.getId(), name(), formatted);
        } catch (Exception e) {
            logger.error("图片识别失败: url={}", imageUrl, e);
            return new ToolResult(call.getId(), name(),
                    "图片识别失败: " + e.getMessage());
        }
    }

    /**
     * 校验图片 URL 是否来自已配置的 S3 存储域名。
     * 防止 SSRF：LLM 可能伪造 tool_calls.arguments 中的 image_url。
     */
    private boolean isAllowedImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return false;

        String urlPrefix = s3Properties.getUrlPrefix();
        if (urlPrefix == null || urlPrefix.isBlank()) return false;

        try {
            String allowedHost = URI.create(urlPrefix).getHost();
            String imageHost = URI.create(imageUrl).getHost();
            return allowedHost != null && allowedHost.equalsIgnoreCase(imageHost);
        } catch (IllegalArgumentException e) {
            logger.warn("无法解析图片 URL: {}", imageUrl);
            return false;
        }
    }
}
