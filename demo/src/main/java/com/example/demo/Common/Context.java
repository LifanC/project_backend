package com.example.demo.Common;

import java.util.Map;

public class Context {
    private static final ThreadLocal<Map<String, Object>> DESIGNATED_ROLE = new ThreadLocal<>();

    public static void set(Map<String, Object> text) {
        DESIGNATED_ROLE.set(text);
    }

    public static Map<String, Object> get() {
        return DESIGNATED_ROLE.get();
    }

    public static void clear() {
        DESIGNATED_ROLE.remove();
    }
}
