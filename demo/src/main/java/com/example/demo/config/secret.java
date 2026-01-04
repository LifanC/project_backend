package com.example.demo.config;

import com.example.demo.Mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class secret {

    @Resource
    private UserMapper userMapper;

    // 這個方法啟動時會自動執行
    @PostConstruct
    public void initSecret() {
        getSecret();
    }

    public void getSecret() {
        LocalDate localDate = new Date().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        int rocYear = localDate.getYear() - 1911;
        String dateFormat = String.format(
                "%03d%02d%02d",
                rocYear,
                localDate.getMonthValue(),
                localDate.getDayOfMonth()
        );
        userMapper.delNoSecret(dateFormat);
        String dateFormatStr = dateFormat.substring(0, 5);
        List<Map<String, Object>> list = userMapper.getSecret(dateFormatStr);
        if (list.isEmpty()) {
            SecureRandom random = new SecureRandom();
            // 產生 32 bytes = 256 bits
            // JWT HS256 最小要求就是 256 bits
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String secretNum = Base64.getEncoder().encodeToString(bytes);
            userMapper.createSecret(secretNum, dateFormat);
        }

    }

}
