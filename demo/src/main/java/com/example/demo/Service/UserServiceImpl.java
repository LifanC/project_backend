package com.example.demo.Service;

import com.example.demo.Mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final String tokens = "tokens";

    @Value("${jwt.expiration}")
    private long expirationSeconds;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, Object> register(Map<String, Object> map) {
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect != null) {
            logger.info("帳號已存在");
            throw new RuntimeException("帳號已存在");
        }
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
        String password = map.get("password").toString();
        map.put("password", passwordEncoder.encode(password));
        map.put("update_date", dateFormat);
        map.put("time_stamp", date);
        userMapper.create(map);
        return map;
    }

    @Override
    public String login(Map<String, Object> map) {
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect == null) {
            logger.info("帳號不存在");
            throw new RuntimeException("帳號不存在");
        }
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        String userPassword = userSelect.get("password").toString();
        if (!passwordEncoder.matches(password, userPassword)) {
            throw new RuntimeException("密碼錯誤");
        }
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
        Map<String, Object> secretMap = userMapper.getSecretOnly(dateFormat);
        SecretKey key =
                Keys.hmacShaKeyFor(
                        secretMap.get("secret_number").toString().getBytes(StandardCharsets.UTF_8)
                );
        String token = Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + expirationSeconds * 1000L))
                .signWith(key)
                .compact();
        stringRedisTemplate.opsForValue().set(tokens, token, expirationSeconds, TimeUnit.SECONDS);
        logger.info("{}: {}", tokens, stringRedisTemplate.opsForValue().get(tokens));
        logger.info("{}: {}", tokens, stringRedisTemplate.getExpire(tokens, TimeUnit.SECONDS));
        return token;
    }

    private SecretKey getKeyForToday() {
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
        String secret = userMapper.getSecretOnly(dateFormat).get("secret_number").toString();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean validateToken(String token) {
        logger.info("{} : {}", tokens, token);
        try {
            SecretKey key = getKeyForToday();
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String username = claims.getSubject();
            logger.info("username: {}", username);

            // Redis 控制登出（token 是否存在）
            return stringRedisTemplate.hasKey(tokens);

        } catch (JwtException e) {
            return false; // JWT 不合法
        }
    }

    public void logout(String token) {
        logger.info("{} : {}", tokens, token);
        stringRedisTemplate.delete(tokens);
    }

}
