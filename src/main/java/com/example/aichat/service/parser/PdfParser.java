package com.example.aichat.service.parser;

import com.example.aichat.config.OcrFailedException;
import com.example.aichat.config.props.OcrProperties;
import com.example.aichat.config.props.PdfImageProperties;
import com.example.aichat.service.ImageService;
import com.example.aichat.service.ocr.ImagePreprocessor;
import com.example.aichat.service.ocr.OcrCacheService;
import com.example.aichat.service.ocr.OcrPostProcessor;
import com.example.aichat.service.ocr.PdfImageCacheService;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import technology.tabula.*;
import technology.tabula.extractors.BasicExtractionAlgorithm;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * PDF 解析器。
 * 优先 PDFBox 提取文字层 → 提取嵌入图片并视觉识别 → 无效则 OCR 缓存 → Tesseract OCR → 后处理。
 */
@Component
public class PdfParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);
    private static final int MAX_PAGES = 100;
    /** 单页渲染像素上限（100M pixels），防止解压炸弹 OOM */
    private static final long MAX_PAGE_PIXELS = 100_000_000L;
    /** 页级并行阈值：超过此页数启用并行 OCR */
    private static final int PARALLEL_THRESHOLD = 5;
    /** PDFBox 提取文本有效性的最小有效字符数 */
    private static final int MIN_VALID_CHARS = 10;
    /** PDFBox 提取文本中有效字符的最低占比 */
    private static final double MIN_VALID_RATIO = 0.3;

    /** 视觉识别提示词 */
    private static final String IMAGE_RECOGNITION_PROMPT =
            "请详细描述这张图片中的内容，包括图表类型、关键数据、流程步骤、架构关系等。" +
            "如果是文字截图，请提取其中的文字。请用中文回答。";

    private final OcrProperties ocrProps;
    private final PdfImageProperties pdfImageProps;
    private final Tesseract tesseract;
    private final ImagePreprocessor preprocessor;
    private final OcrPostProcessor postProcessor;
    private final OcrCacheService cacheService;
    private final PdfImageCacheService imageCacheService;
    private final ImageService imageService;
    /** OCR 专用线程池（4 线程，支持页级并行） */
    private final ExecutorService ocrExecutor = Executors.newFixedThreadPool(4);
    /** 图片识别线程池（4 线程，网络 IO 密集型） */
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);
    /** 线程级强制 OCR 标志（由 KnowledgeBaseService 在上传时设置） */
    private final ThreadLocal<Boolean> forceOcrFlag = ThreadLocal.withInitial(() -> false);

    public PdfParser(OcrProperties ocrProps, PdfImageProperties pdfImageProps,
                     Tesseract tesseract, ImagePreprocessor preprocessor,
                     OcrPostProcessor postProcessor, OcrCacheService cacheService,
                     PdfImageCacheService imageCacheService, ImageService imageService) {
        this.ocrProps = ocrProps;
        this.pdfImageProps = pdfImageProps;
        this.tesseract = tesseract;
        this.preprocessor = preprocessor;
        this.postProcessor = postProcessor;
        this.cacheService = cacheService;
        this.imageCacheService = imageCacheService;
        this.imageService = imageService;
    }

    /** 设置当前线程的强制 OCR 标志，处理完成后调用方负责清理 */
    public void setForceOcrForCurrentThread(boolean force) {
        forceOcrFlag.set(force);
    }

    @Override
    public String parse(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        String pdfHash = imageCacheService.sha256(bytes);
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            // 强制 OCR 模式
            if (Boolean.TRUE.equals(forceOcrFlag.get()) && ocrProps.isEnabled()) {
                log.info("强制 OCR 模式: {}", filePath.getFileName());
                return doOcrWithCache(bytes, doc);
            }

            // 优先 PDFBox 提取
            String text = extractText(doc);

            // 提取并识别嵌入图片（P0: 文字有效的 PDF 才做图片识别）
            if (pdfImageProps.isEnabled() && text != null && !text.isBlank()) {
                try {
                    String imageDescriptions = extractAndRecognizeImages(bytes, pdfHash, doc);
                    if (!imageDescriptions.isEmpty()) {
                        text = text + "\n\n" + imageDescriptions;
                    }
                } catch (Exception e) {
                    log.warn("PDF 图片识别失败，跳过: {}", filePath.getFileName(), e);
                }
            }

            // 文本无效则回退 OCR
            if (!isTextValid(text) && ocrProps.isEnabled()) {
                log.info("PDFBox 提取无效，回退 OCR: {}", filePath.getFileName());
                text = doOcrWithCache(bytes, doc);
            }

            return text;
        } catch (IOException e) {
            log.error("PDF 解析失败: {}", filePath.getFileName(), e);
            throw new RuntimeException("PDF 解析失败: " + e.getMessage(), e);
        } finally {
            forceOcrFlag.remove();
        }
    }

    // ==================== 图片提取与识别（P0） ====================

    /**
     * 提取 PDF 中嵌入的图片，过滤后调用视觉 API 识别，返回格式化描述文本。
     */
    private String extractAndRecognizeImages(byte[] pdfBytes, String pdfHash, PDDocument doc) {
        // 1. 提取图片
        List<ImageInfo> images = extractImages(doc);
        if (images.isEmpty()) return "";

        log.debug("PDF 提取到 {} 张原始嵌入图片", images.size());

        // 2. 过滤
        images = filterImages(images);
        if (images.isEmpty()) {
            log.debug("PDF 嵌入图片全部被过滤");
            return "";
        }

        // 3. 限制数量
        if (images.size() > pdfImageProps.getMaxImagesPerDoc()) {
            log.info("PDF 图片数 {} 超过上限 {}，截取前 {} 张",
                    images.size(), pdfImageProps.getMaxImagesPerDoc(), pdfImageProps.getMaxImagesPerDoc());
            images = images.subList(0, pdfImageProps.getMaxImagesPerDoc());
        }

        // 4. 识别（含缓存）
        Map<Integer, String> descriptions = recognizeImages(pdfHash, images);

        // 5. 格式化输出
        return formatImageDescriptions(descriptions);
    }

    /** 遍历 PDF 所有页面，提取 PDImageXObject */
    private List<ImageInfo> extractImages(PDDocument doc) {
        List<ImageInfo> images = new ArrayList<>();
        int globalIdx = 0;

        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            PDResources resources = page.getResources();
            if (resources == null) continue;

            try {
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xobj = resources.getXObject(name);
                    if (xobj instanceof PDImageXObject) {
                        PDImageXObject imgXObj = (PDImageXObject) xobj;
                        BufferedImage image = imgXObj.getImage();
                        if (image != null) {
                            images.add(new ImageInfo(i + 1, globalIdx++, image));
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("第 {} 页图片资源提取异常: {}", i + 1, e.getMessage());
            }
        }
        return images;
    }

    /** 过滤：尺寸、宽高比、纯色、哈希去重 */
    private List<ImageInfo> filterImages(List<ImageInfo> images) {
        List<ImageInfo> result = new ArrayList<>();
        Set<String> seenHashes = new HashSet<>();

        int minW = pdfImageProps.getMinWidth();
        int minH = pdfImageProps.getMinHeight();

        for (ImageInfo img : images) {
            int w = img.image.getWidth();
            int h = img.image.getHeight();

            // 尺寸过滤
            if (w < minW || h < minH) continue;

            // 极端宽高比过滤
            double ratio = (double) w / h;
            if (ratio > 10 || ratio < 0.1) continue;

            // 纯色检测
            if (isSolidColor(img.image)) continue;

            // 哈希去重
            byte[] imgBytes = imageToPngBytes(img.image);
            if (imgBytes == null) continue;
            img.imageBytes = imgBytes;
            img.imageHash = imageCacheService.sha256(imgBytes);
            if (!seenHashes.add(img.imageHash)) continue;

            result.add(img);
        }
        log.debug("过滤后保留 {} 张图片（原始 {} 张）", result.size(), images.size());
        return result;
    }

    /** 纯色检测：采样像素，唯一颜色数 < 16 视为纯色块 */
    private boolean isSolidColor(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int sampleStep = Math.max(1, Math.min(w, h) / 20);
        Set<Integer> colors = new HashSet<>();

        for (int y = 0; y < h; y += sampleStep) {
            for (int x = 0; x < w; x += sampleStep) {
                colors.add(image.getRGB(x, y));
                if (colors.size() >= 16) return false;
            }
        }
        return colors.size() < 16;
    }

    /** 识别图片：先查缓存，未命中则调用视觉 API（支持并行） */
    private Map<Integer, String> recognizeImages(String pdfHash, List<ImageInfo> images) {
        if (images.size() == 1) {
            // 单张：顺序处理
            String desc = recognizeSingle(pdfHash, images.get(0));
            Map<Integer, String> result = new LinkedHashMap<>();
            if (!desc.isEmpty()) result.put(images.get(0).pageNum, desc);
            return result;
        }

        // 多张：并行处理
        List<CompletableFuture<Map.Entry<Integer, String>>> futures = new ArrayList<>();
        for (ImageInfo img : images) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> new AbstractMap.SimpleEntry<>(img.pageNum, recognizeSingle(pdfHash, img)),
                    imageExecutor));
        }

        Map<Integer, String> result = new LinkedHashMap<>();
        for (var f : futures) {
            try {
                Map.Entry<Integer, String> entry = f.get(pdfImageProps.getTimeoutSeconds(), TimeUnit.SECONDS);
                if (!entry.getValue().isEmpty()) {
                    result.merge(entry.getKey(), entry.getValue(),
                            (a, b) -> a + "\n" + b);
                }
            } catch (TimeoutException e) {
                log.warn("单张图片识别超时");
            } catch (Exception e) {
                log.warn("单张图片识别异常: {}", e.getMessage());
            }
        }
        return result;
    }

    private String recognizeSingle(String pdfHash, ImageInfo img) {
        // 查缓存
        Optional<String> cached = imageCacheService.get(pdfHash, img.imageHash);
        if (cached.isPresent()) return cached.get();

        // 缩放大图
        byte[] bytes = img.imageBytes;
        if (img.image.getWidth() > pdfImageProps.getMaxDimension()
                || img.image.getHeight() > pdfImageProps.getMaxDimension()) {
            BufferedImage resized = resizeIfNeeded(img.image);
            bytes = imageToPngBytes(resized);
            if (bytes == null) return "";
        }

        try {
            log.debug("调用视觉 API 识别图片: page={}, size={}x{}",
                    img.pageNum, img.image.getWidth(), img.image.getHeight());
            String desc = imageService.recognizeImage(bytes, IMAGE_RECOGNITION_PROMPT);
            if (desc != null && !desc.isBlank()) {
                imageCacheService.put(pdfHash, img.imageHash, desc);
                return desc;
            }
        } catch (Exception e) {
            log.warn("图片视觉识别失败: page={}, {}", img.pageNum, e.getMessage());
        }
        return "";
    }

    /** 等比缩放，确保最大边长不超过 maxDimension */
    private BufferedImage resizeIfNeeded(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int maxDim = pdfImageProps.getMaxDimension();
        if (w <= maxDim && h <= maxDim) return src;

        double scale = (double) maxDim / Math.max(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }

    /** BufferedImage 转 PNG 字节数组 */
    private byte[] imageToPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("图片转 PNG 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 格式化图片描述，按页码分组 */
    private String formatImageDescriptions(Map<Integer, String> descriptions) {
        if (descriptions.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[以下为文档中嵌入图片的视觉识别描述]\n");
        for (var entry : descriptions.entrySet()) {
            sb.append("[第 ").append(entry.getKey()).append(" 页图片描述：")
                    .append(entry.getValue()).append("]\n\n");
        }
        return sb.toString().trim();
    }

    // ---- PDFBox 提取 ----

    private String extractText(PDDocument doc) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(extractTables(doc));
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        sb.append(stripper.getText(doc));
        return sb.toString();
    }

    /** 使用 tabula-java 提取 PDF 中的表格，转为 Markdown 表格格式。 */
    private String extractTables(PDDocument doc) throws IOException {
        StringBuilder md = new StringBuilder();
        try {
            ObjectExtractor extractor = new ObjectExtractor(doc);
            PageIterator pages = extractor.extract();
            BasicExtractionAlgorithm bea = new BasicExtractionAlgorithm();

            while (pages.hasNext()) {
                Page page = pages.next();
                List<? extends Table> tables = bea.extract(page);
                for (Table table : tables) {
                    md.append(toMarkdownTable(table)).append("\n\n");
                }
            }
        } catch (Exception e) {
            log.debug("PDF 表格提取跳过: {}", e.getMessage());
        }
        return md.toString();
    }

    private String toMarkdownTable(Table table) {
        StringBuilder md = new StringBuilder();
        int rowCount = table.getRowCount();
        int colCount = table.getColCount();
        if (rowCount == 0) return "";

        for (int r = 0; r < rowCount; r++) {
            md.append("|");
            for (int c = 0; c < colCount; c++) {
                String cell = table.getCell(r, c).getText();
                md.append(cell != null ? cell.trim() : "").append("|");
            }
            md.append("\n");
            if (r == 0) {
                md.append("|");
                for (int c = 0; c < colCount; c++) md.append("---|");
                md.append("\n");
            }
        }
        return md.toString();
    }

    // ---- OCR 回退（含缓存） ----

    /** OCR 入口：先查缓存，未命中则执行 OCR 并缓存结果。 */
    private String doOcrWithCache(byte[] pdfBytes, PDDocument doc) {
        // 先查缓存
        Optional<String> cached = cacheService.get(pdfBytes);
        if (cached.isPresent()) {
            log.info("OCR 缓存命中");
            return cached.get();
        }

        String result = ocrWithTimeout(pdfBytes, doc.getNumberOfPages());
        if (!result.isBlank()) {
            cacheService.put(pdfBytes, result);
        }
        return result;
    }

    /**
     * 带超时的 OCR 识别。
     * @throws OcrFailedException 超时、异常或结果为空
     */
    private String ocrWithTimeout(byte[] pdfBytes, int pages) {
        if (pages > MAX_PAGES) {
            throw OcrFailedException.of("PDF", "页数 " + pages + " 超过上限 " + MAX_PAGES);
        }

        // 长文档页级并行，短文档顺序处理
        Future<String> future = ocrExecutor.submit(() ->
                pages >= PARALLEL_THRESHOLD ? doOcrParallel(pdfBytes, pages) : doOcr(pdfBytes, pages));

        try {
            String result = future.get(ocrProps.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (result.isBlank()) {
                throw OcrFailedException.of("PDF", "OCR 返回空结果");
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw OcrFailedException.of("PDF", "OCR 超时 (" + ocrProps.getTimeoutSeconds() + "s)");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof OcrFailedException) throw (OcrFailedException) cause;
            throw OcrFailedException.of("PDF", "OCR 执行异常: " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OcrFailedException.of("PDF", "OCR 被中断");
        }
    }

    // ---- 顺序 OCR（短文档） ----

    private String doOcr(byte[] pdfBytes, int totalPages) {
        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            tesseract.setVariable("user_defined_dpi", String.valueOf(ocrProps.getDpi()));

            for (int i = 0; i < totalPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, ocrProps.getDpi());
                try {
                    // 像素上限检查：防止解压炸弹
                    long pixels = (long) image.getWidth() * image.getHeight();
                    if (pixels > MAX_PAGE_PIXELS) {
                        log.warn("第 {} 页像素数 {} 超过上限，跳过", i + 1, pixels);
                        continue;
                    }
                    BufferedImage processed = preprocessor.preprocess(image);
                    String pageText = tesseract.doOCR(processed);
                    pageText = postProcessor.postProcess(pageText);
                    sb.append(pageText).append("\n");
                    processed.flush();
                } finally {
                    image.flush();
                }
            }
        } catch (IOException | TesseractException e) {
            throw OcrFailedException.of("PDF", e.getMessage());
        }
        log.info("OCR 识别完成: {} 页, {} 字符", totalPages, sb.length());
        return sb.toString();
    }

    // ---- 页级并行 OCR（长文档，>= 5 页） ----

    private String doOcrParallel(byte[] pdfBytes, int totalPages) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            tesseract.setVariable("user_defined_dpi", String.valueOf(ocrProps.getDpi()));

            List<CompletableFuture<IndexedPage>> futures = new ArrayList<>();
            for (int i = 0; i < totalPages; i++) {
                final int pageIdx = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        BufferedImage image = renderer.renderImageWithDPI(pageIdx, ocrProps.getDpi());
                        try {
                            long pixels = (long) image.getWidth() * image.getHeight();
                            if (pixels > MAX_PAGE_PIXELS) {
                                log.warn("第 {} 页像素数 {} 超过上限，跳过", pageIdx + 1, pixels);
                                return new IndexedPage(pageIdx, "");
                            }
                            BufferedImage processed = preprocessor.preprocess(image);
                            String text = tesseract.doOCR(processed);
                            text = postProcessor.postProcess(text);
                            processed.flush();
                            return new IndexedPage(pageIdx, text);
                        } finally {
                            image.flush();
                        }
                    } catch (IOException | TesseractException e) {
                        log.error("第 {} 页 OCR 失败: {}", pageIdx + 1, e.getMessage());
                        return new IndexedPage(pageIdx, "");
                    }
                }, ocrExecutor));
            }

            // 按页码排序聚合
            String[] pages = new String[totalPages];
            for (var f : futures) {
                try {
                    IndexedPage ip = f.get(ocrProps.getTimeoutSeconds(), TimeUnit.SECONDS);
                    pages[ip.index] = ip.text;
                } catch (Exception e) {
                    log.warn("并行 OCR 聚合异常: {}", e.getMessage());
                }
            }

            String result = String.join("\n", pages);
            log.info("并行 OCR 完成: {} 页, {} 字符", totalPages, result.length());
            return result;
        } catch (IOException e) {
            throw OcrFailedException.of("PDF", e.getMessage());
        }
    }

    private record IndexedPage(int index, String text) {}

    /** 嵌入图片信息 */
    private static class ImageInfo {
        final int pageNum;
        final int imageIndex;
        final BufferedImage image;
        byte[] imageBytes;
        String imageHash;

        ImageInfo(int pageNum, int imageIndex, BufferedImage image) {
            this.pageNum = pageNum;
            this.imageIndex = imageIndex;
            this.image = image;
        }
    }

    // ---- 工具 ----

    /**
     * 判断 PDFBox 提取的文本是否有效。
     * 有效条件：CJK+字母+数字字符 >= {@value #MIN_VALID_CHARS} 且占比 >= {@value #MIN_VALID_RATIO}。
     */
    private static boolean isTextValid(String text) {
        if (text == null || text.isBlank()) return false;

        long validChars = text.codePoints()
                .filter(cp -> Character.isLetterOrDigit(cp) || Character.isIdeographic(cp))
                .count();

        long totalNonSpace = text.codePoints()
                .filter(cp -> !Character.isWhitespace(cp))
                .count();

        return validChars >= MIN_VALID_CHARS
                && (totalNonSpace == 0 || (double) validChars / totalNonSpace >= MIN_VALID_RATIO);
    }

    @Override
    public boolean supports(String fileType) {
        return "pdf".equals(fileType);
    }
}
