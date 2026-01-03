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
        Date date = new Date();
        LocalDate localDate = date.toInstant()
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
        List<Map<String, Object>> list1 =
                userMapper.getSecret(dateFormat.substring(0, 5));
        if (list1.isEmpty()) {
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[32]; // 256 bits
            random.nextBytes(bytes);
            String secretNum = Base64.getEncoder().encodeToString(bytes);
            userMapper.createSecret(secretNum, dateFormat);
        }

    }

}
