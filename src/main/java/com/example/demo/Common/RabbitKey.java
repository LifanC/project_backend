package com.example.demo.Common;

import java.util.Map;

public class RabbitKey {

    // <App>:<Domain>:<Purpose>
    public static final Map<String, String> rabbitKey = Map.of(
            "quotation_create", "quotation:%s:create"
    );

}
