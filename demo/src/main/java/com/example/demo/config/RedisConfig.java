package com.example.demo.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
//        // 建立預設快取配置，設定 TTL 60 秒
//        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
//                .entryTtl(Duration.ofSeconds(60))  // 預設 60 秒過期
//                .disableCachingNullValues();       // 可選：不快取 null 值
//
//        // 建立 RedisCacheManager 並套用預設配置
//        return RedisCacheManager.builder(redisConnectionFactory)
//                .cacheDefaults(config)
//                .build();

        // 建立預設快取配置，設定 TTL 60 秒
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                // 不指定 value serializer（用預設 JDK）
                .entryTtl(Duration.ofSeconds(60)) // 預設 60 秒過期
                .disableCachingNullValues();      // 可選：不快取 null 值

        // 建立 RedisCacheManager 並套用預設配置
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();

    }

}

