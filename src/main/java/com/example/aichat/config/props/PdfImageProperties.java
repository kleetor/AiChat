package com.example.aichat.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pdf.image.recognition")
public class PdfImageProperties {
    /** 是否启用 PDF 嵌入图片视觉识别 */
    private boolean enabled = true;
    /** 最小图片宽度（px），小于此值的图片跳过 */
    private int minWidth = 150;
    /** 最小图片高度（px），小于此值的图片跳过 */
    private int minHeight = 150;
    /** 单文档最多识别的图片数 */
    private int maxImagesPerDoc = 50;
    /** 单张图片识别超时（秒） */
    private int timeoutSeconds = 30;
    /** 最大图片边长（px），超过则等比缩放后识别，节省 token */
    private int maxDimension = 2048;
}
