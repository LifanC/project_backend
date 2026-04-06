package com.example.demo.Exception;

import com.example.demo.Common.ConvertFormat;
import com.example.demo.Dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // DTO 驗證失敗
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handleValidationException(
            MethodArgumentNotValidException ex) {
        logger.error(ex.getMessage(), ex);

        Map<String, String> fieldErrors = new TreeMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> {
                    fieldErrors.put(
                            error.getField(),
                            error.getDefaultMessage()
                    );
                });

        Map<String, Map<Integer, Object>> message = msg("參數驗證失敗");
        message.put("error", ConvertFormat.convert(List.of(fieldErrors)));
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        message
                ));
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<?> handleResourceAlreadyExists(ResourceAlreadyExistsException ex) {
        logger.error(ex.getMessage(), ex);
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        msg(ex.getMessage())
                ));
    }

    @ExceptionHandler(IsViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handleIsViolation(IsViolationException ex) {
        logger.error(ex.getMessage(), ex);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        msg(ex.getMessage())
                ));
    }

    @ExceptionHandler(DBException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> handleDBException(DBException ex) {
        logger.error(ex.getMessage(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        msg(ex.getMessage())
                ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        logger.error(ex.getMessage(), ex);
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        msg(ex.getMessage())
                ));
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<?> handleBadRequest(BadRequestException ex) {
        logger.error(ex.getMessage(), ex);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        msg(ex.getMessage())
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        logger.error(ex.getMessage(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        msg(ex.getMessage())
                ));
    }

    private Map<String, Map<Integer, Object>> msg(String ex) {
        Map<String, Map<Integer, Object>> message = new TreeMap<>();
        message.put("content", ConvertFormat.convert(List.of(ex)));
        return message;
    }

}
