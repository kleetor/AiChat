package com.example.aichat.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {
    /** 是否启用 OCR 识别扫描件 */
    private boolean enabled = true;
    /** Tesseract 语言包路径（Docker 内为 /usr/share/tessdata） */
    private String tessdataPath = "./tessdata";
    /** 识别语言 */
    private String language = "chi_sim+eng";
    /** 渲染 DPI（250 平衡精度与内存，300→250 内存降低约 30%） */
    private int dpi = 250;
    /** 单文档 OCR 最大秒数 */
    private int timeoutSeconds = 300;
}
