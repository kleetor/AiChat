package com.example.aichat.service.parser;

import com.example.aichat.service.ocr.ImagePreprocessor;
import com.example.aichat.service.ocr.OcrCacheService;
import com.example.aichat.service.ocr.OcrPostProcessor;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * 图片解析器：使用 Tesseract OCR 识别图片中的文字。
 * 支持 jpg / jpeg / png / tiff / tif / bmp 格式。
 */
// @Component — 前端仅开放 TXT/MD/PDF/DOCX，图片格式暂时隐藏
public class ImageParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(ImageParser.class);
    private static final Set<String> SUPPORTED = Set.of("jpg", "jpeg", "png", "tiff", "tif", "bmp");

    /** 单边最大像素数，超过此值自动降采样 */
    private static final int MAX_DIMENSION = 3000;
    /** 像素上限（100M），防止超大图 OOM */
    private static final long MAX_PIXELS = 100_000_000L;

    private final Tesseract tesseract;
    private final ImagePreprocessor preprocessor;
    private final OcrPostProcessor postProcessor;
    private final OcrCacheService cacheService;

    public ImageParser(Tesseract tesseract,
                       ImagePreprocessor preprocessor,
                       OcrPostProcessor postProcessor,
                       OcrCacheService cacheService) {
        this.tesseract = tesseract;
        this.preprocessor = preprocessor;
        this.postProcessor = postProcessor;
        this.cacheService = cacheService;
    }

    @Override
    public String parse(Path filePath) throws IOException {
        long parseStart = System.currentTimeMillis();
        String fileName = filePath.getFileName().toString();

        // 先读字节，用于缓存 key
        byte[] bytes = Files.readAllBytes(filePath);
        log.info("[图片] 开始解析: {} ({}MB)", fileName, bytes.length / 1024 / 1024);

        // 查 OCR 缓存
        Optional<String> cached = cacheService.get(bytes);
        if (cached.isPresent()) {
            log.info("[图片] OCR 缓存命中: {} 字符", cached.get().length());
            return cached.get();
        }

        logMemory("图片-加载前");
        BufferedImage image;
        try {
            image = ImageIO.read(filePath.toFile());
        } catch (IOException e) {
            log.error("[图片] 无法读取: {}", fileName, e);
            throw new IOException("图片文件损坏或格式不支持: " + fileName, e);
        }
        if (image == null) {
            throw new IOException("无法解码图片: " + fileName + "（文件可能已损坏或格式不受支持）");
        }

        int origW = image.getWidth(), origH = image.getHeight();
        long pixels = (long) origW * origH;
        log.info("[图片] 加载完成: {}x{} ({}M像素)", origW, origH, pixels / 1_000_000);
        logMemory("图片-加载后");

        // 像素上限检查
        if (pixels > MAX_PIXELS) {
            image.flush();
            throw new IOException(String.format("图片过大: %dx%d (%dM像素)，超过上限100M", origW, origH, pixels / 1_000_000));
        }

        // 降采样（大图自动缩小到 MAX_DIMENSION 以内）
        BufferedImage source = downscaleIfNeeded(image, origW, origH);
        if (source != image) {
            image.flush();
            log.info("[图片] 降采样: {}x{} → {}x{}", origW, origH, source.getWidth(), source.getHeight());
        }

        try {
            long ocrStart = System.currentTimeMillis();
            logMemory("图片-OCR开始前");

            BufferedImage processed = preprocessor.preprocess(source);
            String result = tesseract.doOCR(processed);
            result = postProcessor.postProcess(result);
            processed.flush();

            long elapsed = System.currentTimeMillis() - ocrStart;
            log.info("[图片] OCR 完成: {} 字符, {}ms", result.length(), elapsed);
            logMemory("图片-OCR完成后");

            // 缓存结果
            if (!result.isBlank()) {
                cacheService.put(bytes, result);
            }

            log.info("[图片] 解析完成: {}, 总耗时{}ms", fileName, System.currentTimeMillis() - parseStart);
            return result;
        } catch (TesseractException e) {
            log.error("[图片] OCR 失败: {}", fileName, e);
            throw new IOException("图片 OCR 识别失败: " + e.getMessage(), e);
        } finally {
            source.flush();
            System.gc();
        }
    }

    /** 大图等比降采样至 MAX_DIMENSION 以内 */
    private BufferedImage downscaleIfNeeded(BufferedImage src, int w, int h) {
        if (w <= MAX_DIMENSION && h <= MAX_DIMENSION) return src;

        double scale = (double) MAX_DIMENSION / Math.max(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, newW, newH, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static void logMemory(String label) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        log.info("[MEM] {} used={}MB max={}MB ({}%)",
                label, used, max, max > 0 ? used * 100 / max : 0);
    }

    @Override
    public boolean supports(String fileType) {
        return SUPPORTED.contains(fileType);
    }
}
