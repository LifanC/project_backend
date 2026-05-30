package com.example.demo.Config;

import com.example.demo.Mapper.SecretMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@Transactional
public class SecretKey {

    private final Logger logger = LoggerFactory.getLogger(SecretKey.class);

    @Resource
    private SecretMapper secretMapper;

    // 這個方法啟動時會自動執行
    @PostConstruct
    public void getSecret() {
        boolean cnt = secretMapper.getSecret();
        if (cnt) {
            logger.info("SecretKey 資料已存在");
        } else {
            SecureRandom random = new SecureRandom();
            // 產生 32 bytes = 256 bits
            // JWT HS256 最小要求就是 256 bits
            byte[] bytes = new byte[64];
            random.nextBytes(bytes);
            String secretNum = Base64.getEncoder().encodeToString(bytes);
            secretMapper.createSecret(secretNum);
        }

    }

}
