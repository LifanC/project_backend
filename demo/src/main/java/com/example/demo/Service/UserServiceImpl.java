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

    private String localDateFormat(Date date) {
        LocalDate localDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        int rocYear = localDate.getYear() - 1911;
        return String.format(
                "%03d%02d%02d",
                rocYear,
                localDate.getMonthValue(),
                localDate.getDayOfMonth()
        );
    }

    private SecretKey getKeyForToday(Date date) {
        String dateFormat = localDateFormat(date);
        String secret = userMapper.getSecretOnly(dateFormat).get("secret_number").toString();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Map<String, Object> register(Map<String, Object> map) {
        Map<String, Object> ui = new HashMap<>();
        ui.put("register", true);
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            ui.put("register", false);
            ui.put("msg", "\n註冊 帳號密碼未輸入");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect != null) {
            ui.put("register", false);
            ui.put("msg", "\n註冊 帳號已存在");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        Date date = new Date();
        map.put("password", passwordEncoder.encode(password));
        map.put("update_date", localDateFormat(date));
        map.put("time_stamp", date);
        userMapper.create(map);
        ui.put("msg", "\n帳號:" + username + "\n密碼" + hiddenCode(password));
        return ui;
    }

    @Override
    public Map<String, Object> updatePassword(Map<String, Object> map) {
        Map<String, Object> ui = new HashMap<>();
        ui.put("updatePassword", true);
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            ui.put("updatePassword", false);
            ui.put("msg", "\n登入 帳號密碼未輸入");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect == null) {
            ui.put("updatePassword", false);
            ui.put("msg", "\n登入 帳號不存在");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        // JWT 簽名與驗證用的「祕密字串（secret）」
        Date date = new Date();
        SecretKey key = getKeyForToday(date);
        String token = Jwts.builder()
                .setSubject(username)
                .setExpiration(
                        Date.from(Instant.now().plus(expirationSeconds, ChronoUnit.SECONDS))
                )
                .signWith(key)
                .compact();
        stringRedisTemplate.opsForValue().set(username, token, expirationSeconds, TimeUnit.SECONDS);
        map.put("password", passwordEncoder.encode(password));
        map.put("update_date", localDateFormat(date));
        map.put("time_stamp", date);
        userMapper.update(map);
        map.put("token", token);
        userMapper.updateToken(map);
        ui.put("msg", "\n" + token);
        return ui;
    }

    @Override
    public Map<String, Object> login(Map<String, Object> map) {
        Map<String, Object> ui = new HashMap<>();
        ui.put("login", true);
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            ui.put("login", false);
            ui.put("msg", "\n登入 帳號密碼未輸入");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect == null) {
            ui.put("login", false);
            ui.put("msg", "\n登入 帳號不存在");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        String userPassword = userSelect.get("password").toString();
        if (!passwordEncoder.matches(password, userPassword)) {
            ui.put("login", false);
            ui.put("msg", "\n登入 密碼錯誤");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        String usertoken = stringRedisTemplate.opsForValue().get(username);
        if (StringUtils.isNotBlank(usertoken)) {
            ui.put("msg", "\n" + usertoken);
            return ui;
        }
        // JWT 簽名與驗證用的「祕密字串（secret）」
        SecretKey key = getKeyForToday(new Date());
        String token = Jwts.builder()
                .setSubject(username)
                .setExpiration(
                        Date.from(Instant.now().plus(expirationSeconds, ChronoUnit.SECONDS))
                )
                .signWith(key)
                .compact();
        stringRedisTemplate.opsForValue().set(username, token, expirationSeconds, TimeUnit.SECONDS);
        map.put("token", token);
        userMapper.updateToken(map);
        ui.put("msg", "\n" + token);
        return ui;
    }

    @Override
    public Map<String, Object> validateToken(String token) {
        Map<String, Object> ui = new HashMap<>();
        ui.put("validate", true);
        if (StringUtils.isBlank(token)) {
            ui.put("validate", false);
            ui.put("msg", "\n驗證 token未輸入");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        String username;
        boolean tokensIsNotBlank;
        try {
            SecretKey key = getKeyForToday(new Date());
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
            if (selectUsername == null) {
                ui.put("msg", "\n無效");
                return ui;
            }
            username = selectUsername.get("username").toString();
            // Redis 控制登出（token 是否存在）
            tokensIsNotBlank = stringRedisTemplate.hasKey(username);
        }
        ui.put("msg", "\n" + (tokensIsNotBlank ? "有效" : "無效"));
        return ui;
    }

    @Override
    public Map<String, Object> query(Map<String, Object> map) {
        Map<String, Object> ui = new HashMap<>();
        ui.put("query", true);
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            ui.put("query", false);
            ui.put("msg", "\n查詢 帳號密碼未輸入");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        Map<String, Object> userSelect = userMapper.select(map);
        if (userSelect == null) {
            ui.put("query", false);
            ui.put("msg", "\n查詢 帳號不存在");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        String username = map.get("username").toString();
        String password = map.get("password").toString();
        String userPassword = userSelect.get("password").toString();
        if (!passwordEncoder.matches(password, userPassword)) {
            ui.put("query", false);
            ui.put("msg", "\n查詢 密碼錯誤");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        Map<String, Object> selectToken = userMapper.selectToken(username);
        String token = "";
        if (selectToken != null) {
            token = selectToken.get("token").toString();
        }
        ui.put("msg", "\n" + token);
        return ui;
    }

    @Override
    public Map<String, Object> logout(String token) {
        Map<String, Object> ui = new HashMap<>();
        ui.put("logout", true);
        if (StringUtils.isBlank(token)) {
            ui.put("logout", false);
            ui.put("msg", "\n登出 token未輸入");
            logger.info(ui.get("msg").toString());
            return ui;
        }
        String username;
        try {
            SecretKey key = getKeyForToday(new Date());
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
        userMapper.updateToken(updateMap);
        ui.put("msg", "\n已登出");
        return ui;
    }

}
