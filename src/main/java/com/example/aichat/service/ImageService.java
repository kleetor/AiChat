package com.example.aichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.S3Configuration;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

    @Value("${s3.endpoint}")
    private String s3Endpoint;

    @Value("${s3.access-key}")
    private String s3AccessKey;

    @Value("${s3.secret-key}")
    private String s3SecretKey;

    @Value("${s3.bucket-name}")
    private String s3BucketName;

    @Value("${s3.url-prefix}")
    private String s3UrlPrefix;

    @Value("${s3.region}")
    private String s3Region;

    @Value("${image.model}")
    private String imageModel;

    @Value("${image.api.key}")
    private String imageApiKey;

    @Value("${image.api.url}")
    private String imageApiUrl;

    private S3Client s3Client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PostConstruct
    public void init() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(s3AccessKey, s3SecretKey);
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(s3Region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        logger.info("S3客户端初始化完成，端点: {}, 存储桶: {}", s3Endpoint, s3BucketName);
    }

    /**
     * 上传图片到S3存储桶，返回公开访问URL
     */
    public String uploadImage(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString() + extension;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3BucketName)
                .key(fileName)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String imageUrl = s3UrlPrefix + "/" + fileName;
        logger.info("图片上传成功: {}", imageUrl);
        return imageUrl;
    }

    /**
     * 调用图片识别模型，返回图片描述
     */
    public String recognizeImage(String imageUrl) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", imageModel);

        ArrayNode messagesArray = objectMapper.createArrayNode();

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a helpful assistant.");
        messagesArray.add(systemMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode contentArray = objectMapper.createArrayNode();

        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", "这张图片里有什么?请详细描述。");
        contentArray.add(textPart);

        ObjectNode imagePart = objectMapper.createObjectNode();
        imagePart.put("type", "image_url");
        ObjectNode imageUrlObj = objectMapper.createObjectNode();
        imageUrlObj.put("url", imageUrl);
        imagePart.set("image_url", imageUrlObj);
        contentArray.add(imagePart);

        userMsg.set("content", contentArray);
        messagesArray.add(userMsg);

        requestBody.set("messages", messagesArray);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        logger.debug("图片识别请求: {}", jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageApiUrl))
                .header("Authorization", "Bearer " + imageApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            logger.error("图片识别API返回错误: HTTP {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("图片识别失败: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.get("choices").get(0).get("message").get("content").asText();
        logger.info("图片识别成功，描述长度: {}", content.length());
        return content;
    }

    /**
     * 上传图片并识别，返回图片URL和描述
     */
    public ImageUploadResult uploadAndRecognize(MultipartFile file) throws Exception {
        String imageUrl = uploadImage(file);
        String description = recognizeImage(imageUrl);
        String formattedDescription = formatImageDescription(imageUrl, description);
        return new ImageUploadResult(imageUrl, formattedDescription);
    }

    /**
     * 将图片描述格式化为伪装消息，让主聊天模型识别为图片描述而非用户原始输入
     */
    private String formatImageDescription(String imageUrl, String description) {
        return "[系统提示：用户上传了一张图片，以下是AI视觉模型对该图片的识别描述，"
                + "请将此描述作为用户上传图片的内容来理解和回应]\n"
                + "图片URL: " + imageUrl + "\n"
                + "图片描述:\n" + description
                + "\n[图片描述结束]";
    }

    public static class ImageUploadResult {
        private final String imageUrl;
        private final String formattedDescription;

        public ImageUploadResult(String imageUrl, String formattedDescription) {
            this.imageUrl = imageUrl;
            this.formattedDescription = formattedDescription;
        }

        public String getImageUrl() { return imageUrl; }
        public String getFormattedDescription() { return formattedDescription; }
    }
}
