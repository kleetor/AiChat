package com.example.aichat.service.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * OCR 图像预处理管线，提升 Tesseract 对低质量扫描件的识别率。
 * 管线顺序：灰度化 → Otsu 二值化 → 中值滤波降噪 → 倾斜校正。
 */
@Component
public class ImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ImagePreprocessor.class);
    /** 倾斜校正最小角度阈值（度），小于此值跳过旋转 */
    private static final double MIN_SKEW_DEGREES = 1.0;

    public BufferedImage preprocess(BufferedImage src) {
        long start = System.currentTimeMillis();
        int w = src.getWidth(), h = src.getHeight();

        BufferedImage gray = toGrayscale(src);
        src = null; // 帮助 GC

        BufferedImage binary = otsuThreshold(gray);
        gray.flush();
        gray = null;

        BufferedImage denoised = medianFilter(binary, 3);
        binary.flush();
        binary = null;

        BufferedImage result = deskew(denoised);
        if (result != denoised) {
            denoised.flush();
        }
        denoised = null;

        long elapsed = System.currentTimeMillis() - start;
        log.debug("图像预处理完成: {}x{} → {}x{}, {}ms", w, h, result.getWidth(), result.getHeight(), elapsed);
        return result;
    }

    // ---- 步骤1: 灰度化 ----

    /**
     * 转为 TYPE_BYTE_GRAY 灰度图，内存从 4 字节/像素降为 1 字节/像素。
     * 若已是灰度图则直接返回。
     */
    private BufferedImage toGrayscale(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) return src;
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        src.flush();
        return gray;
    }

    // ---- 步骤2: Otsu 自适应二值化 ----

    private BufferedImage otsuThreshold(BufferedImage gray) {
        int w = gray.getWidth(), h = gray.getHeight();
        // 计算直方图
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                hist[gray.getRaster().getSample(x, y, 0)]++;
            }
        }
        // Otsu 算法求阈值
        int total = w * h;
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * hist[i];
        double sumB = 0;
        int wB = 0;
        double maxVariance = 0;
        int threshold = 128; // 默认
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0 || wB == total) continue;
            int wF = total - wB;
            sumB += t * hist[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double varBetween = (double) wB * wF * (mB - mF) * (mB - mF);
            if (varBetween > maxVariance) {
                maxVariance = varBetween;
                threshold = t;
            }
        }
        // 应用二值化
        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = gray.getRaster().getSample(x, y, 0);
                binary.getRaster().setSample(x, y, 0, pixel > threshold ? 1 : 0);
            }
        }
        return binary;
    }

    // ---- 步骤3: 中值滤波降噪 ----

    private BufferedImage medianFilter(BufferedImage src, int kernelSize) {
        int w = src.getWidth(), h = src.getHeight();
        int half = kernelSize / 2;
        BufferedImage dst = new BufferedImage(w, h, src.getType());
        int[] window = new int[kernelSize * kernelSize];

        for (int y = half; y < h - half; y++) {
            for (int x = half; x < w - half; x++) {
                int idx = 0;
                for (int dy = -half; dy <= half; dy++) {
                    for (int dx = -half; dx <= half; dx++) {
                        window[idx++] = src.getRaster().getSample(x + dx, y + dy, 0);
                    }
                }
                java.util.Arrays.sort(window);
                dst.getRaster().setSample(x, y, 0, window[window.length / 2]);
            }
        }
        // 边缘像素直接复制
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x < half || x >= w - half || y < half || y >= h - half) {
                    dst.getRaster().setSample(x, y, 0, src.getRaster().getSample(x, y, 0));
                }
            }
        }
        return dst;
    }

    // ---- 步骤4: 倾斜校正 ----

    private BufferedImage deskew(BufferedImage src) {
        double angle = detectSkewAngle(src);
        if (Math.abs(angle) < MIN_SKEW_DEGREES) return src;
        log.debug("检测到倾斜 {:.1f}°，执行校正", angle);
        return rotate(src, -angle);
    }

    /**
     * 通过霍夫变换简化版检测倾斜角：遍历 -10°~10°，
     * 对每行投影求方差，方差最大者为最佳角度。
     */
    private double detectSkewAngle(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        double bestAngle = 0, maxVariance = 0;

        for (double angle = -10.0; angle <= 10.0; angle += 0.5) {
            double rad = Math.toRadians(angle);
            double[] projection = new double[h];
            for (int y = 0; y < h; y++) {
                int blackCount = 0;
                for (int x = 0; x < w; x++) {
                    int shiftedY = (int) (y + x * Math.tan(rad));
                    if (shiftedY >= 0 && shiftedY < h && src.getRaster().getSample(x, shiftedY, 0) == 0) {
                        blackCount++;
                    }
                }
                projection[y] = blackCount;
            }
            // 计算投影方差
            double mean = 0;
            for (double p : projection) mean += p;
            mean /= h;
            double variance = 0;
            for (double p : projection) variance += (p - mean) * (p - mean);
            variance /= h;
            if (variance > maxVariance) {
                maxVariance = variance;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }

    private BufferedImage rotate(BufferedImage src, double degrees) {
        double rad = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(rad)), cos = Math.abs(Math.cos(rad));
        int w = src.getWidth(), h = src.getHeight();
        int newW = (int) Math.ceil(w * cos + h * sin);
        int newH = (int) Math.ceil(h * cos + w * sin);

        BufferedImage dst = new BufferedImage(newW, newH, src.getType());
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.translate((newW - w) / 2.0, (newH - h) / 2.0);
            g.rotate(rad, w / 2.0, h / 2.0);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }
}
