package com.example.aichat.service.ocr;

import com.example.aichat.service.parser.PdfParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImagePreprocessor 预处理逻辑测试。
 * 仅测试核心算法步骤，不覆盖完整管线（完整管线依赖 PDF 渲染）。
 */
class ImagePreprocessorTest {

    private final ImagePreprocessor preprocessor = new ImagePreprocessor();

    @Test
    @DisplayName("灰度化 → TYPE_BYTE_GRAY 输出")
    void toGrayscaleReturnsByteGray() throws Exception {
        BufferedImage src = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Method m = ImagePreprocessor.class.getDeclaredMethod("toGrayscale", BufferedImage.class);
        m.setAccessible(true);
        BufferedImage result = (BufferedImage) m.invoke(preprocessor, src);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, result.getType());
    }

    @Test
    @DisplayName("已是灰度图 → 直接返回")
    void alreadyGrayscaleReturnsSelf() throws Exception {
        BufferedImage src = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        Method m = ImagePreprocessor.class.getDeclaredMethod("toGrayscale", BufferedImage.class);
        m.setAccessible(true);
        BufferedImage result = (BufferedImage) m.invoke(preprocessor, src);
        assertSame(src, result);
    }

    @Test
    @DisplayName("Otsu 二值化 → TYPE_BYTE_BINARY 输出")
    void otsuThresholdReturnsBinary() throws Exception {
        // 创建半黑半白的灰度图
        BufferedImage gray = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 100; y++) {
            for (int x = 0; x < 100; x++) {
                gray.getRaster().setSample(x, y, 0, x < 50 ? 0 : 255);
            }
        }

        Method m = ImagePreprocessor.class.getDeclaredMethod("otsuThreshold", BufferedImage.class);
        m.setAccessible(true);
        BufferedImage result = (BufferedImage) m.invoke(preprocessor, gray);

        assertEquals(BufferedImage.TYPE_BYTE_BINARY, result.getType());
        // 左半应为黑色(0)，右半应为白色(1)
        assertEquals(0, result.getRaster().getSample(10, 50, 0));
        assertEquals(1, result.getRaster().getSample(60, 50, 0));
    }

    @Test
    @DisplayName("Otsu 全黑图 → 阈值正确")
    void otsuAllBlack() throws Exception {
        BufferedImage gray = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 10; y++)
            for (int x = 0; x < 10; x++)
                gray.getRaster().setSample(x, y, 0, 0);

        Method m = ImagePreprocessor.class.getDeclaredMethod("otsuThreshold", BufferedImage.class);
        m.setAccessible(true);
        BufferedImage result = (BufferedImage) m.invoke(preprocessor, gray);

        assertNotNull(result);
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, result.getType());
    }

    @Test
    @DisplayName("中值滤波 3×3 → 消除孤立噪点")
    void medianFilterRemovesIsolatedNoise() throws Exception {
        // 创建一个 5×5 的二值图，中心点有噪点
        BufferedImage src = new BufferedImage(5, 5, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < 5; y++)
            for (int x = 0; x < 5; x++)
                src.getRaster().setSample(x, y, 0, 0); // 全黑
        src.getRaster().setSample(2, 2, 0, 1); // 中心一个白点（噪点）

        Method m = ImagePreprocessor.class.getDeclaredMethod("medianFilter", BufferedImage.class, int.class);
        m.setAccessible(true);
        BufferedImage result = (BufferedImage) m.invoke(preprocessor, src, 3);

        // 中心噪点应被消除（周围8个像素都是0，中位数为0）
        assertEquals(0, result.getRaster().getSample(2, 2, 0));
    }

    @Test
    @DisplayName("preprocess 全管线 → 不抛异常")
    void fullPipelineDoesNotThrow() {
        BufferedImage src = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        // 画一些文字模拟
        for (int x = 20; x < 80; x++) {
            src.setRGB(x, 50, 0x000000);
        }

        assertDoesNotThrow(() -> {
            BufferedImage result = preprocessor.preprocess(src);
            assertNotNull(result);
        });
    }
}
