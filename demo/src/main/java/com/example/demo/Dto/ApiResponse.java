package com.example.demo.Dto;

import com.example.demo.Common.ConvertFormat;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiResponse {

    public static Map<String, Object> api(HttpStatus code, Map<String, Map<Integer, Object>> messageMap) {
        return Map.of(
                "code", code,
                "status", code.value(),
                "message", messageMap,
                "timestamp", ConvertFormat.time("")
        );
    }

}
