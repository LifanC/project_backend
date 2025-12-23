package com.example.demo.redis;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceImpl implements RedisService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void set(String key, Object value) {
        if (value instanceof List) {
            // 存 List
            redisTemplate.opsForList().rightPushAll(key, (List<?>) value);
        } else {
            // 存單值
            redisTemplate.opsForValue().set(key, value);
        }
    }

    @Override
    public void set(String key, Object value, long timeoutSeconds) {
        if (value instanceof List) {
            redisTemplate.opsForList().rightPushAll(key, (List<?>) value);
            redisTemplate.expire(key, timeoutSeconds, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, value, timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public Object get(String key) {
        String type = redisTemplate.type(key).code().toUpperCase();
        return switch (type) {
            case "STRING" -> {
                ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
                yield valueOps.get(key);
            }
            case "HASH" -> {
                HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
                yield hashOps.entries(key);
            }
            case "LIST" -> {
                ListOperations<String, Object> listOps = redisTemplate.opsForList();
                yield listOps.range(key, 0, -1);
            }
            case "SET" -> {
                SetOperations<String, Object> setOps = redisTemplate.opsForSet();
                yield setOps.members(key);
            }
            case "ZSET" -> {
                ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
                yield zSetOps.range(key, 0, -1);
            }
            default -> null;
        };
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

}

