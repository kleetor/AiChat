package com.example.aichat.config;

/**
 * OCR 识别失败异常，由 GlobalExceptionHandler 统一转换为前端 ERROR 状态。
 * 消息直接写入 KbDocument.errorMsg，前端轮询可见。
 */
public class OcrFailedException extends RuntimeException {

    public OcrFailedException(String message) {
        super(message);
    }

    public OcrFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建一个携带上下文信息的 OCR 失败异常。
     * @param fileName 文件名
     * @param detail  具体原因（如 "超时" / "无有效文本"）
     */
    public static OcrFailedException of(String fileName, String detail) {
        return new OcrFailedException("OCR 识别失败: " + fileName + " - " + detail);
    }
}
