package com.example.demo.Exception;

public class IsViolationException extends RuntimeException {
    public IsViolationException(String message) {
        super(message);
    }
    public IsViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
