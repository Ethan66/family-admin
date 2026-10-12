package com.ethan.familyadmin.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数不正确", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "无权执行此操作", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    BUSINESS_ERROR("BUSINESS_ERROR", "操作失败", HttpStatus.CONFLICT),
    SYSTEM_ERROR("SYSTEM_ERROR", "系统繁忙，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

