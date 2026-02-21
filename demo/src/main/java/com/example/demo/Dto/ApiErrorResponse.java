package com.example.demo.Dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

public class ApiErrorResponse {

    private final HttpStatus code;

    private final int status;

    private final String message;

    private final Map<String, String> errors;

    private final LocalDateTime timestamp;

    public ApiErrorResponse(
            HttpStatus code,
            String message,
            Map<String, String> errors) {
        this.code = code;
        this.status = code.value();
        this.message = message;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }

    public String getCode() {
        return code.name();
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
