package com.example.demo.Common;

import java.util.TreeMap;
import java.util.List;
import java.util.Map;

public class ConvertFormat {

    public static Map<Integer, Object> convert(List<Object> message) {
        Map<Integer, Object> messageMap = new TreeMap<>();
        for (int i = 0; i < message.size(); i++) {
            messageMap.put(i + 1, message.get(i));
        }
        return messageMap;
    }

}
