package com.example.demo.Dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

public class ApiResponse {

    public static Map<String, Object> api(HttpStatus code, Map<String, Map<Integer, Object>> messageMap) {
        return Map.of(
                "code", code,
                "status", code.value(),
                "message", messageMap,
                "timestamp", LocalDateTime.now()
        );
    }

}
