package com.example.aichat.config;

import org.springframework.http.HttpStatus;

/**
 * 业务异常，携带 HTTP 状态码和用户可见消息，
 * 由 GlobalExceptionHandler 统一转换为前端响应。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    // ---- 常用工厂方法 ----

    /** 400 - 参数校验 / 业务规则违反 */
    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    /** 401 - 未认证（一般由 Security 处理，供手动场景使用） */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, message);
    }

    /** 403 - 无权操作 */
    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }

    /** 404 - 资源不存在 */
    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    /** 409 - 冲突（重复注册、重复点赞等） */
    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    /** 429 - 频率限制 */
    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
