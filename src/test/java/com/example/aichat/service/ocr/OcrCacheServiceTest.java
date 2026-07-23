package com.example.aichat.service.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OcrCacheService 缓存逻辑测试。
 */
class OcrCacheServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("写入后读取 → 命中缓存")
    void cacheHitAfterPut() {
        OcrCacheService service = new OcrCacheService(tempDir);
        byte[] data = "test pdf content".getBytes();

        assertTrue(service.get(data).isEmpty());

        service.put(data, "OCR 识别结果文本");
        var cached = service.get(data);
        assertTrue(cached.isPresent());
        assertEquals("OCR 识别结果文本", cached.get());
    }

    @Test
    @DisplayName("不同内容 → 不同缓存 key")
    void differentContentDifferentCache() {
        OcrCacheService service = new OcrCacheService(tempDir);
        byte[] data1 = "content A".getBytes();
        byte[] data2 = "content B".getBytes();

        service.put(data1, "result A");
        service.put(data2, "result B");

        assertEquals("result A", service.get(data1).orElseThrow());
        assertEquals("result B", service.get(data2).orElseThrow());
    }

    @Test
    @DisplayName("空白结果 → 不写入缓存")
    void blankResultNotCached() {
        OcrCacheService service = new OcrCacheService(tempDir);
        byte[] data = "empty result test".getBytes();

        service.put(data, "");
        service.put(data, "   ");

        assertTrue(service.get(data).isEmpty());
    }

    @Test
    @DisplayName("相同内容两次 put → 覆盖")
    void sameContentOverwrite() {
        OcrCacheService service = new OcrCacheService(tempDir);
        byte[] data = "overwrite test".getBytes();

        service.put(data, "first version");
        service.put(data, "second version");

        assertEquals("second version", service.get(data).orElseThrow());
    }

    @Test
    @DisplayName("相同哈希不同缓存 → 互相隔离")
    void cacheIsolationAcrossInstances() {
        OcrCacheService s1 = new OcrCacheService(tempDir.resolve("cache1"));
        OcrCacheService s2 = new OcrCacheService(tempDir.resolve("cache2"));
        byte[] data = "shared content".getBytes();

        s1.put(data, "from cache1");
        s2.put(data, "from cache2");

        assertEquals("from cache1", s1.get(data).orElseThrow());
        assertEquals("from cache2", s2.get(data).orElseThrow());
    }
}
