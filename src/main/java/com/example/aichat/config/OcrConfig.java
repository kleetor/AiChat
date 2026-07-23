package com.example.aichat.config;

import com.example.aichat.config.props.OcrProperties;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OCR 配置：将 Tesseract 注册为 Spring Bean 单例，供 PdfParser / ImageParser 复用。
 */
@Configuration
public class OcrConfig {

    @Bean
    public Tesseract tesseract(OcrProperties props) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(props.getTessdataPath());
        tesseract.setLanguage(props.getLanguage());
        tesseract.setVariable("user_defined_dpi", String.valueOf(props.getDpi()));
        return tesseract;
    }
}
