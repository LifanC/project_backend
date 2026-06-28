package com.example.demo.Dto;

import com.example.demo.Common.ConvertFormat;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class ApiResponse {

    public static Map<String, Object> api(HttpStatus code, List<Map<String, Object>> data) {
        return Map.of(
                "code", code,
                "status", code.value(),
                "data", data,
                "timestamp", ConvertFormat.time("")
        );
    }

}
