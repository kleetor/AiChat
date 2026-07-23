package com.example.aichat.service.parser;

import com.example.aichat.service.ocr.ImagePreprocessor;
import com.example.aichat.service.ocr.OcrPostProcessor;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * 图片解析器：使用 Tesseract OCR 识别图片中的文字。
 * 支持 jpg / jpeg / png / tiff / tif / bmp 格式。
 */
@Component
public class ImageParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(ImageParser.class);
    private static final Set<String> SUPPORTED = Set.of("jpg", "jpeg", "png", "tiff", "tif", "bmp");

    private final Tesseract tesseract;
    private final ImagePreprocessor preprocessor;
    private final OcrPostProcessor postProcessor;

    public ImageParser(Tesseract tesseract,
                       ImagePreprocessor preprocessor,
                       OcrPostProcessor postProcessor) {
        this.tesseract = tesseract;
        this.preprocessor = preprocessor;
        this.postProcessor = postProcessor;
    }

    @Override
    public String parse(Path filePath) throws IOException {
        BufferedImage image;
        try {
            image = ImageIO.read(filePath.toFile());
        } catch (IOException e) {
            log.error("无法读取图片文件: {}", filePath, e);
            throw new IOException("图片文件损坏或格式不支持: " + filePath.getFileName(), e);
        }
        if (image == null) {
            throw new IOException("无法解码图片: " + filePath.getFileName() + "（文件可能已损坏或格式不受支持）");
        }
        try {
            BufferedImage processed = preprocessor.preprocess(image);
            String result = tesseract.doOCR(processed);
            result = postProcessor.postProcess(result);
            log.info("图片 OCR 识别完成: {}, {} 字符", filePath.getFileName(), result.length());
            processed.flush();
            return result;
        } catch (TesseractException e) {
            log.error("图片 OCR 识别失败: {}", filePath.getFileName(), e);
            throw new IOException("图片 OCR 识别失败: " + e.getMessage(), e);
        } finally {
            image.flush();
        }
    }

    @Override
    public boolean supports(String fileType) {
        return SUPPORTED.contains(fileType);
    }
}
