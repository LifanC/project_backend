package com.example.demo.redis;

public interface RedisService {

    // 儲存單值或 List
    void set(String key, Object value);

    // 設定過期時間（秒）
    void set(String key, Object value, long timeoutSeconds);

    // 取得單值或 List
    Object get(String key);

    // 刪除
    void delete(String key);

}

