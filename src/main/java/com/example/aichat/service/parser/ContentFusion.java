package com.example.aichat.service.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多路内容智能融合器。
 * 将 PDFBox 文字、视觉模型描述、OCR 结果按图片类型融合为统一输出。
 */
final class ContentFusion {

    private static final Logger log = LoggerFactory.getLogger(ContentFusion.class);

    private ContentFusion() {}

    /**
     * 融合多路结果为统一文本。
     *
     * @param pdfBoxText    PDFBox 文字层提取结果
     * @param imageResults  视觉模型分类+描述（key=页码）
     * @param ocrTexts      嵌入图 OCR 结果（key=页码）
     * @param pageOcrText   页面级 OCR 回退结果
     * @return 融合后的纯文本
     */
    static String merge(String pdfBoxText,
                        Map<Integer, List<PdfParser.ImageResult>> imageResults,
                        Map<Integer, String> ocrTexts,
                        String pageOcrText) {

        StringBuilder sb = new StringBuilder();

        // 1. PDFBox 文字层（优先，在最前面）
        if (pdfBoxText != null && !pdfBoxText.isBlank()) {
            sb.append(pdfBoxText.strip()).append("\n");
        }

        // 2. 嵌入图片结果（按页码排序）
        if (!imageResults.isEmpty()) {
            // 有图片描述时加分隔
            if (!sb.isEmpty()) sb.append("\n");

            imageResults.keySet().stream().sorted().forEach(pageNum -> {
                List<PdfParser.ImageResult> results = imageResults.get(pageNum);
                for (int i = 0; i < results.size(); i++) {
                    PdfParser.ImageResult r = results.get(i);
                    String typeLabel = toTypeLabel(r.type());

                    switch (r.type()) {
                        case CHART, DIAGRAM, PHOTO -> {
                            // 视觉描述
                            sb.append(String.format("[第%d页 - %s]\n%s\n",
                                    pageNum, typeLabel, r.description()));
                        }
                        case TABLE -> {
                            // 视觉概述 + OCR 精确数据
                            sb.append(String.format("[第%d页 - %s]\n概述: %s\n",
                                    pageNum, typeLabel, r.description()));
                            String ocr = ocrTexts.get(pageNum);
                            if (ocr != null && !ocr.isBlank()) {
                                sb.append("[精确数据]\n").append(ocr.strip()).append("\n");
                            }
                        }
                        case TEXT_DOCUMENT -> {
                            // OCR 为主，视觉描述作为元信息
                            sb.append(String.format("[第%d页 - %s]\n主题: %s\n",
                                    pageNum, typeLabel, r.description()));
                            String ocr = ocrTexts.get(pageNum);
                            if (ocr != null && !ocr.isBlank()) {
                                sb.append("[OCR 识别全文]\n").append(ocr.strip()).append("\n");
                            }
                        }
                    }
                    sb.append("\n");
                }
            });
        }

        // 3. 页面级 OCR 回退（扫描件 PDF，放在最后）
        if (pageOcrText != null && !pageOcrText.isBlank()) {
            if (!sb.isEmpty() && !sb.toString().endsWith("\n\n")) {
                sb.append("\n");
            }
            sb.append("[以下为页面级 OCR 识别内容]\n").append(pageOcrText.strip()).append("\n");
        }

        log.debug("[融合] PDFBox={}字符, 图片结果={}页, OCR嵌入={}页, OCR页面={}字符",
                pdfBoxText != null ? pdfBoxText.length() : 0,
                imageResults.size(),
                ocrTexts.size(),
                pageOcrText != null ? pageOcrText.length() : 0);

        return sb.toString().trim();
    }

    private static String toTypeLabel(PdfParser.ImageType type) {
        return switch (type) {
            case CHART -> "图表";
            case DIAGRAM -> "流程图/示意图";
            case PHOTO -> "图片";
            case TABLE -> "表格";
            case TEXT_DOCUMENT -> "扫描件/文档截图";
        };
    }
}
