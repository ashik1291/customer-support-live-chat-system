package com.example.chat.service.exception;

import org.springframework.http.HttpStatus;

public class ServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ServiceException(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    public ServiceException(HttpStatus status, String message, Throwable cause) {
        this(status, message, null, cause);
    }

    public ServiceException(HttpStatus status, String message, String errorCode) {
        this(status, message, errorCode, null);
    }

    public ServiceException(HttpStatus status, String message, String errorCode, Throwable cause) {
        super(message, cause, false, status.is5xxServerError());
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
