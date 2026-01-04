package com.example.demo.Service;

import com.example.demo.Mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.common.util.StringUtils;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${jwt.expiration}")
    private long expirationSeconds;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String hiddenCode(String password) {
        if (password.length() <= 2) {
            password = "*".repeat(password.length());
        } else {
            password =
                    password.charAt(0) +
                            "*".repeat(password.length() - 2) +
                            password.charAt(password.length() - 1);
        }
        return password;
    }

    @Override
    public Map<String, Object> register(Map<String, Object> map) {
        map.put("register", true);
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            map.put("register", false);
            map.put("msg", "\n註冊 帳號密碼未輸入");
            logger.info(map.get("msg").toString());
            return map;
        }
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect != null) {
            map.put("register", false);
            map.put("msg", "\n註冊 帳號已存在");
            logger.info(map.get("msg").toString());
            return map;
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
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        map.put("password", passwordEncoder.encode(password));
        map.put("update_date", dateFormat);
        map.put("time_stamp", date);
        userMapper.create(map);
        Map<String, Object> ui = new HashMap<>();
        ui.put("register", true);
        ui.put("msg", "\n帳號:" + username + "\n密碼" + hiddenCode(password));
        return ui;
    }

    @Override
    public Map<String, Object> login(Map<String, Object> map) {
        map.put("login", true);
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            map.put("login", false);
            map.put("msg", "\n登入 帳號密碼未輸入");
            logger.info(map.get("msg").toString());
            return map;
        }
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect == null) {
            map.put("login", false);
            map.put("msg", "\n登入 帳號不存在");
            logger.info(map.get("msg").toString());
            return map;
        }
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        String userPassword = userSelect.get("password").toString();
        if (!passwordEncoder.matches(password, userPassword)) {
            map.put("login", false);
            map.put("msg", "\n登入 密碼錯誤");
            logger.info(map.get("msg").toString());
            return map;
        }
        String usertoken = stringRedisTemplate.opsForValue().get(username);
        if (StringUtils.isNotBlank(usertoken)) {
            map.put("token", usertoken);
            return map;
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
        // JWT 簽名與驗證用的「祕密字串（secret）」
        Map<String, Object> secretMap = userMapper.getSecretOnly(dateFormat);
        SecretKey key =
                Keys.hmacShaKeyFor(
                        secretMap.get("secret_number").toString().getBytes(StandardCharsets.UTF_8)
                );
        String token = Jwts.builder()
                .setSubject(username)
                .setExpiration(
                        Date.from(Instant.now().plus(expirationSeconds, ChronoUnit.SECONDS))
                )
                .signWith(key)
                .compact();
        stringRedisTemplate.opsForValue().set(username, token, expirationSeconds, TimeUnit.SECONDS);
        map.put("token", token);
        userMapper.update(map);
        Map<String, Object> ui = new HashMap<>();
        ui.put("login", true);
        ui.put("msg", "\n" + token);
        return ui;
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
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> map = new HashMap<>();
        map.put("validate", true);
        if (StringUtils.isBlank(token)) {
            map.put("validate", false);
            map.put("msg", "\n驗證 token未輸入");
            logger.info(map.get("msg").toString());
            return map;
        }
        String username;
        boolean tokensIsNotBlank;
        try {
            SecretKey key = getKeyForToday();
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setAllowedClockSkewSeconds(expirationSeconds)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            username = claims.getSubject();
            logger.info("驗證 username: {} : {}", username, token);
            // Redis 控制登出（token 是否存在）
            tokensIsNotBlank = stringRedisTemplate.hasKey(username);
        } catch (JwtException e) {
            // JWT 不合法
            logger.info("validate JwtException : {}", e.getMessage());
            Map<String, Object> selectUsername = userMapper.selectUsername(token);
            username = selectUsername.get("username").toString();
            // Redis 控制登出（token 是否存在）
            tokensIsNotBlank = stringRedisTemplate.hasKey(username);
        }
        Map<String, Object> ui = new HashMap<>();
        ui.put("validate", true);
        ui.put("msg", "\n" + (tokensIsNotBlank ? "有效" : "無效"));
        return ui;
    }

    @Override
    public Map<String, Object> query(Map<String, Object> map) {
        map.put("query", true);
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            map.put("query", false);
            map.put("msg", "\n查詢 帳號密碼未輸入");
            logger.info(map.get("msg").toString());
            return map;
        }
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect == null) {
            map.put("query", false);
            map.put("msg", "\n查詢 帳號不存在");
            logger.info(map.get("msg").toString());
            return map;
        }
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        String userPassword = userSelect.get("password").toString();
        if (!passwordEncoder.matches(password, userPassword)) {
            map.put("query", false);
            map.put("msg", "\n查詢 密碼錯誤");
            logger.info(map.get("msg").toString());
            return map;
        }
        Map<String, Object> selectToken = userMapper.selectToken(username);
        String token = "";
        if (selectToken != null) {
            token = selectToken.get("token").toString();
        }
        Map<String, Object> ui = new HashMap<>();
        ui.put("query", true);
        ui.put("msg", "\n" + token);
        return ui;
    }

    @Override
    public Map<String, Object> logout(String token) {
        Map<String, Object> map = new HashMap<>();
        map.put("logout", true);
        if (StringUtils.isBlank(token)) {
            map.put("logout", false);
            map.put("msg", "\n登出 token未輸入");
            logger.info(map.get("msg").toString());
            return map;
        }
        String username;
        try {
            SecretKey key = getKeyForToday();
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setAllowedClockSkewSeconds(expirationSeconds)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            username = claims.getSubject();
        } catch (JwtException e) {
            // JWT 不合法
            logger.info("logout JwtException : {}", e.getMessage());
            Map<String, Object> selectUsername = userMapper.selectUsername(token);
            username = selectUsername.get("username").toString();
        }
        logger.info("登出 username: {} : {}", username, token);
        stringRedisTemplate.delete(username);
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("username", username);
        updateMap.put("token", null);
        userMapper.update(updateMap);
        Map<String, Object> ui = new HashMap<>();
        ui.put("logout", true);
        ui.put("msg", "\n已登出");
        return ui;
    }

}
