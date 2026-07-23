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
 * PDF 解析器 — 视觉模型前置管线。
 *
 * <pre>
 * Phase 0: 嵌入图片元数据提取（不加载像素）
 * Phase 1: PDFBox 文字层提取
 * Phase 2: 视觉模型前置（分类 + 描述，逐张流式）
 * Phase 3: OCR 按需执行（仅 table / text_document 类型）
 * Phase 4: 页面级 OCR 回退（扫描件 PDF）
 * Phase 5: ContentFusion 智能融合
 * </pre>
 */
@Component
public class PdfParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);
    private static final int MAX_PAGES = 100;
    private static final long MAX_PAGE_PIXELS = 100_000_000L;
    private static final int PARALLEL_THRESHOLD = 5;
    private static final int OCR_RENDER_DPI = 200;
    private static final int MIN_VALID_CHARS = 10;
    private static final double MIN_VALID_RATIO = 0.3;

    /** 视觉模型分类 + 描述 prompt */
    private static final String CLASSIFY_AND_DESCRIBE_PROMPT = """
            请分析这张图片：

            1. 判断类型（严格选择一项）：
               - chart: 统计图表（柱状图、折线图、饼图、散点图等数据可视化）
               - diagram: 流程图、架构图、思维导图、示意图
               - photo: 照片、自然图像、非文字为主的截图
               - table: 表格、数据表、价目表、清单（以行列结构呈现）
               - text_document: 文本文档截图、扫描件（以文字内容为主）

            2. 描述内容：
               - 如果是 chart: 描述图表类型、关键数据趋势和数值
               - 如果是 diagram: 描述流程/架构的关键节点和关系
               - 如果是 photo: 描述画面内容
               - 如果是 table: 概述表格结构和主要数据类别
               - 如果是 text_document: 概述文档主题和关键信息

            请用中文，按以下格式回答：
            TYPE: <类型>
            DESC: <描述内容>""";

    private final OcrProperties ocrProps;
    private final PdfImageProperties pdfImageProps;
    private final Tesseract tesseract;
    private final ImagePreprocessor preprocessor;
    private final OcrPostProcessor postProcessor;
    private final OcrCacheService cacheService;
    private final PdfImageCacheService imageCacheService;
    private final ImageService imageService;
    private final ExecutorService ocrExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);

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

    // ==================== 数据模型 ====================

    /** 嵌入图片元数据（仅尺寸/页码，不加载像素，~100B/条） */
    private record ImageMeta(int pageNum, int width, int height, PDImageXObject xobj) {}

    /** 视觉模型分类 + 描述结果 */
    record ImageResult(ImageType type, String description) {}

    enum ImageType {
        CHART, DIAGRAM, PHOTO,
        TABLE, TEXT_DOCUMENT;

        static ImageType from(String label) {
            return switch (label.strip().toLowerCase()) {
                case "chart" -> CHART;
                case "diagram" -> DIAGRAM;
                case "photo" -> PHOTO;
                case "table" -> TABLE;
                case "text_document" -> TEXT_DOCUMENT;
                default -> PHOTO;
            };
        }

        boolean needsOcr() { return this == TABLE || this == TEXT_DOCUMENT; }
    }

    // ==================== 主入口 ====================

    @Override
    public String parse(Path filePath) throws IOException {
        long parseStart = System.currentTimeMillis();
        byte[] bytes = Files.readAllBytes(filePath);
        String fileName = filePath.getFileName().toString();
        log.info("[PDF] 开始解析: {} ({}MB)", fileName, bytes.length / 1024 / 1024);
        logMemory("parse-入口");

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            log.info("[PDF] PDDocument 加载完成: {} 页", doc.getNumberOfPages());

            // === Phase 0: 元数据提取（不加载像素） ===
            List<ImageMeta> metas = List.of();
            if (pdfImageProps.isEnabled()) {
                metas = extractImageMeta(doc);
                metas = filterByMetadata(metas);
                log.info("[图片] 元数据提取: {} 张有效图片", metas.size());
            }
            logMemory("Phase0-完成");

            // === Phase 1: PDFBox 文字提取 ===
            String pdfBoxText = extractText(doc);
            boolean textValid = isTextValid(pdfBoxText);
            log.info("[PDF] PDFBox 文字提取: {} 字符, 有效={}", pdfBoxText != null ? pdfBoxText.length() : 0, textValid);
            logMemory("Phase1-完成");

            // D2: 纯扫描件检测 — PDFBox 无效 且 无嵌入图 → 跳过图片分支，仅页面 OCR
            if (!textValid && metas.isEmpty() && ocrProps.isEnabled()) {
                log.info("[PDF] 检测为纯扫描件 PDF，跳过嵌入图片处理");
                String result = doOcrWithCache(bytes, doc);
                log.info("[PDF] 解析完成: {}, 总耗时{}ms", fileName, System.currentTimeMillis() - parseStart);
                return result;
            }

            // === Phase 2: 视觉模型前置（分类 + 描述，逐张流式） ===
            Map<Integer, List<ImageResult>> imageResults = new LinkedHashMap<>();
            List<ImageMeta> ocrCandidates = new ArrayList<>();
            int consecutiveFailures = 0;

            for (ImageMeta meta : metas) {
                BufferedImage image = null;
                try {
                    image = meta.xobj.getImage();
                    if (isSolidColor(image)) continue;

                    byte[] imgBytes = prepareImageBytes(image);
                    if (imgBytes == null) continue;

                    ImageResult result = classifyAndDescribe(imgBytes, meta.pageNum);
                    if (result != null) {
                        consecutiveFailures = 0;
                        imageResults.computeIfAbsent(meta.pageNum, k -> new ArrayList<>()).add(result);
                        if (result.type().needsOcr()) {
                            ocrCandidates.add(meta);
                        }
                        log.debug("[视觉] 第{}页: type={}, descLen={}", meta.pageNum, result.type(), result.description().length());
                    } else {
                        // D3: 连续失败 → 剩余图片降级为 OCR 回退
                        consecutiveFailures++;
                        if (consecutiveFailures >= 3) {
                            log.warn("[视觉] 连续 {} 次 API 失败，剩余图片降级为 OCR 回退", consecutiveFailures);
                            int remainingIdx = metas.indexOf(meta) + 1;
                            for (int j = remainingIdx; j < metas.size(); j++) {
                                ocrCandidates.add(metas.get(j));
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("[视觉] 第{}页图片处理异常: {}", meta.pageNum, e.getMessage());
                    consecutiveFailures++;
                } finally {
                    if (image != null) image.flush();
                }
            }
            log.info("[视觉] Phase2 完成: {} 张分类结果, {} 张 OCR 候选", imageResults.values().stream().mapToInt(List::size).sum(), ocrCandidates.size());
            logMemory("Phase2-完成");

            // === Phase 3: OCR 按需执行（仅 table / text_document） ===
            Map<Integer, String> ocrTexts = new LinkedHashMap<>();
            for (ImageMeta meta : ocrCandidates) {
                BufferedImage image = null;
                try {
                    image = meta.xobj.getImage();
                    BufferedImage processed = preprocessor.preprocess(image);
                    String text = tesseract.doOCR(processed);
                    text = postProcessor.postProcess(text);
                    if (!text.isBlank()) {
                        ocrTexts.merge(meta.pageNum, text, (a, b) -> a + "\n" + b);
                    }
                    processed.flush();
                    log.debug("[OCR] 嵌入图第{}页: {} 字符", meta.pageNum, text.length());
                } catch (Exception e) {
                    log.warn("[OCR] 嵌入图第{}页失败: {}", meta.pageNum, e.getMessage());
                } finally {
                    if (image != null) image.flush();
                }
            }
            log.info("[OCR] Phase3 完成: {} 页有 OCR 结果", ocrTexts.size());
            logMemory("Phase3-完成");

            // === Phase 4: 页面级 OCR 回退（扫描件 PDF，或 PDFBox 文字层无效） ===
            String pageOcrText = "";
            if (!textValid && ocrProps.isEnabled()) {
                log.info("[PDF] PDFBox 文字无效，回退页面级 OCR");
                pageOcrText = doOcrWithCache(bytes, doc);
                logMemory("Phase4-完成");
            }

            // === Phase 5: 智能融合 ===
            String result = ContentFusion.merge(pdfBoxText, imageResults, ocrTexts, pageOcrText);
            log.info("[PDF] 解析完成: {}, 总文本{}字符, 总耗时{}ms", fileName,
                    result != null ? result.length() : 0, System.currentTimeMillis() - parseStart);
            logMemory("parse-退出");
            return result;
        } catch (IOException e) {
            log.error("[PDF] 解析失败: {}", fileName, e);
            throw new RuntimeException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    // ==================== Phase 0: 元数据提取 ====================

    /**
     * 只读 PDImageXObject 尺寸和页码，不调用 getImage() 解压像素。
     * 单条 ~100B，1000 张图片也仅 ~100KB。
     */
    private List<ImageMeta> extractImageMeta(PDDocument doc) {
        List<ImageMeta> metas = new ArrayList<>();
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            PDResources resources = page.getResources();
            if (resources == null) continue;
            try {
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xobj = resources.getXObject(name);
                    if (xobj instanceof PDImageXObject img) {
                        metas.add(new ImageMeta(i + 1, img.getWidth(), img.getHeight(), img));
                    }
                }
            } catch (IOException e) {
                log.warn("第 {} 页图片资源元数据提取异常: {}", i + 1, e.getMessage());
            }
        }
        return metas;
    }

    /** 元数据级别过滤：尺寸、宽高比（零像素开销） */
    private List<ImageMeta> filterByMetadata(List<ImageMeta> metas) {
        int minW = pdfImageProps.getMinWidth();
        int minH = pdfImageProps.getMinHeight();
        int maxCount = pdfImageProps.getMaxImagesPerDoc();

        List<ImageMeta> filtered = metas.stream()
                .filter(m -> m.width >= minW && m.height >= minH)
                .filter(m -> {
                    double ratio = (double) m.width / m.height;
                    return ratio <= 10 && ratio >= 0.1;
                })
                .toList();

        log.debug("元数据过滤: {} → {} 张（尺寸≥{}x{}, 宽高比正常）", metas.size(), filtered.size(), minW, minH);

        if (filtered.size() > maxCount) {
            log.info("图片数量 {} 超过上限 {}，截取前 {}", filtered.size(), maxCount, maxCount);
            return filtered.subList(0, maxCount);
        }
        return filtered;
    }

    // ==================== Phase 2: 视觉模型分类 + 描述 ====================

    /** 将 BufferedImage 转为 PNG 字节，必要时缩放 */
    private byte[] prepareImageBytes(BufferedImage image) {
        BufferedImage source = image;
        if (image.getWidth() > pdfImageProps.getMaxDimension()
                || image.getHeight() > pdfImageProps.getMaxDimension()) {
            source = resizeIfNeeded(image);
        }
        return imageToPngBytes(source);
    }

    /** 调用视觉 API 分类 + 描述 */
    private ImageResult classifyAndDescribe(byte[] imgBytes, int pageNum) {
        // 查缓存（使用旧缓存 key 的 hash，保持兼容）
        String imgHash = imageCacheService.sha256(imgBytes);
        Optional<String> cached = imageCacheService.get(imgHash, imgHash); // 简化：用 imgHash 替代 pdfHash+imgHash
        if (cached.isPresent()) {
            return parseClassifyResponse(cached.get(), pageNum);
        }

        try {
            String response = imageService.recognizeImage(imgBytes, CLASSIFY_AND_DESCRIBE_PROMPT);
            if (response == null || response.isBlank()) {
                log.warn("[视觉] 第{}页 API 返回空", pageNum);
                return null;
            }
            // 缓存原始响应
            imageCacheService.put(imgHash, imgHash, response);
            return parseClassifyResponse(response, pageNum);
        } catch (Exception e) {
            log.warn("[视觉] 第{}页 API 异常: {}", pageNum, e.getMessage());
            return null;
        }
    }

    /** 解析视觉模型响应 "TYPE: xxx\nDESC: xxx" */
    ImageResult parseClassifyResponse(String response, int pageNum) {
        String typeLabel = "photo";
        String description = response;

        // 解析 TYPE: 行
        int typeIdx = response.indexOf("TYPE:");
        int newlineAfterType = response.indexOf('\n', typeIdx);
        if (typeIdx >= 0 && newlineAfterType > typeIdx) {
            typeLabel = response.substring(typeIdx + 5, newlineAfterType).strip();
        }

        // 解析 DESC: 行
        int descIdx = response.indexOf("DESC:");
        if (descIdx >= 0) {
            description = response.substring(descIdx + 5).strip();
        } else if (typeIdx >= 0 && newlineAfterType > typeIdx) {
            // 无 DESC 标记，取 TYPE 行后的所有内容
            description = response.substring(newlineAfterType + 1).strip();
        }

        ImageType type = ImageType.from(typeLabel);
        log.debug("[视觉] 第{}页分类: {} → {}", pageNum, typeLabel, type);
        return new ImageResult(type, description);
    }

    // ==================== PDFBox 文字提取 ====================

    private String extractText(PDDocument doc) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(extractTables(doc));
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        sb.append(stripper.getText(doc));
        return sb.toString();
    }

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

    // ==================== OCR 回退（保持不变） ====================

    private String doOcrWithCache(byte[] pdfBytes, PDDocument doc) {
        Optional<String> cached = cacheService.get(pdfBytes);
        if (cached.isPresent()) {
            log.info("[OCR] 缓存命中，跳过识别 ({} 字符)", cached.get().length());
            return cached.get();
        }
        log.info("[OCR] 缓存未命中，开始识别: {} 页, {} DPI", doc.getNumberOfPages(), OCR_RENDER_DPI);
        logMemory("OCR-开始前");
        long ocrStart = System.currentTimeMillis();
        String result = ocrWithTimeout(pdfBytes, doc);
        if (!result.isBlank()) {
            cacheService.put(pdfBytes, result);
        }
        log.info("[OCR] 识别完成: {} 字符, 总耗时{}ms", result.length(), System.currentTimeMillis() - ocrStart);
        logMemory("OCR-完成后");
        return result;
    }

    private String ocrWithTimeout(byte[] pdfBytes, PDDocument doc) {
        int pages = doc.getNumberOfPages();
        if (pages > MAX_PAGES) {
            throw OcrFailedException.of("PDF", "页数 " + pages + " 超过上限 " + MAX_PAGES);
        }
        Future<String> future = ocrExecutor.submit(() ->
                pages >= PARALLEL_THRESHOLD ? doOcrParallel(doc, pages) : doOcr(doc, pages));
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

    private String doOcr(PDDocument doc, int totalPages) {
        StringBuilder sb = new StringBuilder();
        PDFRenderer renderer = new PDFRenderer(doc);
        tesseract.setVariable("user_defined_dpi", String.valueOf(OCR_RENDER_DPI));
        log.info("[OCR] 顺序模式开始: {} 页", totalPages);
        for (int i = 0; i < totalPages; i++) {
            try {
                BufferedImage image = renderer.renderImageWithDPI(i, OCR_RENDER_DPI);
                try {
                    long pixels = (long) image.getWidth() * image.getHeight();
                    if (pixels > MAX_PAGE_PIXELS) {
                        log.warn("[OCR] 第 {} 页像素数 {} 超过上限，跳过", i + 1, pixels);
                        continue;
                    }
                    BufferedImage processed = preprocessor.preprocess(image);
                    String pageText = tesseract.doOCR(processed);
                    pageText = postProcessor.postProcess(pageText);
                    sb.append(pageText).append("\n");
                    processed.flush();
                } catch (TesseractException e) {
                    log.error("[OCR] 第 {} 页 OCR 失败: {}", i + 1, e.getMessage());
                } finally {
                    image.flush();
                }
            } catch (IOException e) {
                log.error("[OCR] 第 {} 页渲染失败: {}", i + 1, e.getMessage());
            }
            if (i > 0 && i % 3 == 0) System.gc();
        }
        System.gc();
        log.info("[OCR] 顺序完成: {} 页, {} 字符", totalPages, sb.length());
        return sb.toString();
    }

    private String doOcrParallel(PDDocument doc, int totalPages) {
        int threads = Math.min(2, Runtime.getRuntime().availableProcessors());
        log.info("[OCR] 并行模式开始: {} 页, {} 线程", totalPages, threads);
        logMemory("OCR并行-开始前");
        ExecutorService parallelExecutor = Executors.newFixedThreadPool(threads);
        List<CompletableFuture<IndexedPage>> futures = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            final int pageIdx = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                PDFRenderer renderer = new PDFRenderer(doc);
                try {
                    BufferedImage image = renderer.renderImageWithDPI(pageIdx, OCR_RENDER_DPI);
                    try {
                        long pixels = (long) image.getWidth() * image.getHeight();
                        if (pixels > MAX_PAGE_PIXELS) {
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
                    log.error("[OCR] 第 {} 页 OCR 失败: {}", pageIdx + 1, e.getMessage());
                    return new IndexedPage(pageIdx, "");
                }
            }, parallelExecutor));
        }
        String[] pages = new String[totalPages];
        for (var f : futures) {
            try {
                IndexedPage ip = f.get(ocrProps.getTimeoutSeconds(), TimeUnit.SECONDS);
                pages[ip.index] = ip.text;
            } catch (Exception e) {
                log.warn("[OCR] 并行聚合异常: {}", e.getMessage());
            }
        }
        parallelExecutor.shutdown();
        String result = String.join("\n", pages);
        System.gc();
        log.info("[OCR] 并行完成: {} 页, {} 字符", totalPages, result.length());
        logMemory("OCR并行-完成后");
        return result;
    }

    private record IndexedPage(int index, String text) {}

    // ==================== 工具方法 ====================

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

    private byte[] imageToPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("图片转 PNG 失败: {}", e.getMessage());
            return null;
        }
    }

    private static void logMemory(String label) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        long total = rt.totalMemory() / 1024 / 1024;
        log.info("[MEM] {} used={}MB total={}MB max={}MB ({}%)",
                label, used, total, max, max > 0 ? used * 100 / max : 0);
    }

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
