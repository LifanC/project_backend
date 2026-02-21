package com.example.demo.Exception;

import com.example.demo.Dto.ApiErrorResponse;
import org.springframework.dao.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // DTO 驗證失敗
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> {
                    fieldErrors.put(
                            error.getField(),
                            error.getDefaultMessage()
                    );
                });

        return new ApiErrorResponse(
                HttpStatus.BAD_REQUEST,
                "參數驗證失敗",
                fieldErrors
        );
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleResourceAlreadyExists(ResourceAlreadyExistsException ex) {

        return new ApiErrorResponse(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                new HashMap<>()
        );
    }

    @ExceptionHandler(IsViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIsViolation(IsViolationException ex) {

        return new ApiErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                new HashMap<>()
        );
    }

    @ExceptionHandler(DBException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleDBException(DBException ex) {

        return new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                new HashMap<>()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ApiErrorResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                new HashMap<>()
        );
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleBadRequest(BadRequestException ex) {
        return new ApiErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                new HashMap<>()
        );
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleRuntimeException(RuntimeException ex) {

        return new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                new HashMap<>()
        );
    }

}
