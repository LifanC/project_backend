package com.example.demo.Common;

import java.util.Map;

public class RedisKey {

    // <App>:<Domain>:<Purpose>:<ID>
    public static final Map<String, String> redisKey = Map.of(
            "refresh", "user:jwt:refresh:{1}",
            "access", "user:jwt:access:{1}:{2}",
            "blacklist", "user:jwt:blacklist:{1}",
            "lock", "user:auth:lock:{1}",
            "fail", "user:auth:fail:{1}"
    );

    // <App>:<Domain>:<Purpose>:<ID>
    public static final Map<String, String> redisPermissionsKey = Map.of(
            "permissionsAll", "permissions:list:all:{1}"
    );

    // <App>:<Domain>:<Purpose>:<ID>
    public static final Map<String, String> redisUserKey = Map.of(
            "userOnly", "user:map:only:{1}"
    );

    // <App>:<Domain>:<Purpose>:<ID>
    public static final Map<String, String> redisCommonAspectKey = Map.of(
            "userRole", "commonAspect:list:role:{1}"
    );

}
