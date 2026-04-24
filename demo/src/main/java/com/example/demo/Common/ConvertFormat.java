package com.example.demo.Common;

import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class ConvertFormat {

    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    private static final DateTimeFormatter OUT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private static final DateTimeFormatter LOCAL =
            new DateTimeFormatterBuilder()
                    .appendPattern("uuuu-MM-dd HH:mm:ss")
                    .optionalStart()
                    .appendPattern(".SSSSSS")
                    .optionalEnd()
                    .toFormatter();

    public static String time(String str) {
        try {

            if (!StringUtils.hasText(str)) {
                return LocalDateTime.now(ZONE).format(OUT);
            }

            LocalDateTime ldt;

            // ✅ 1. ISO UTC
            if (str.endsWith("Z")) {
                ldt = Instant.parse(str)
                        .atZone(ZONE)
                        .toLocalDateTime();
            }
            // ✅ 2. 本地格式
            else {
                ldt = LocalDateTime.parse(str, LOCAL);
            }

            return ldt.format(OUT);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid datetime: " + str, e);
        }
    }

}
