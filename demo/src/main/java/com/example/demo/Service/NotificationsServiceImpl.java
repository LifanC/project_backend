package com.example.demo.Service;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.Context;
import com.example.demo.Common.RedisKey;
import com.example.demo.Common.StatusKey;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Notifications.NotificationsRequset;
import com.example.demo.Dto.User.UserData;
import com.example.demo.Exception.BadRequestException;
import com.example.demo.Exception.ResourceNotFoundException;
import com.example.demo.Mapper.*;
import com.example.demo.Security.Annotation.CheckRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


@Service
public class NotificationsServiceImpl implements NotificationsService {

    private final Logger logger = LoggerFactory.getLogger(NotificationsServiceImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final NotificationsMapper notificationsMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationsServiceImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            RoleMapper roleMapper,
            NotificationsMapper notificationsMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.notificationsMapper = notificationsMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // 單次回源鎖
    private final ReentrantLock lock = new ReentrantLock();

    private SecretKey getKeyForToday() {
        String secret = secretMapper.getSecretOnly();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> getUserData(UserData userData) {
        return userMapper.select(userData).get(userData.getUsername());
    }

    /*
     * 防 Cache Stampede（雪崩）
     * 問題：* 大量 key 同時過期 → DB 被打爆
     * */
    private int expirationSecondsAddRndomNumber() {
        int min = 1;
        int max = 60;
        return Math.toIntExact(expirationSeconds + (new Random().nextInt((max - min) + 1) + min));
    }

    @Override
    public ResponseEntity<?> testLogin() {
        logger.info("notifications/testLogin: notifications is working!");
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> dataMap = new TreeMap<>();
        dataMap.put("status_name", "狀態");
        dataMap.put("status", "notifications is working!");
        data.add(dataMap);
        HttpStatus status = HttpStatus.OK;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        data
                ));
    }

    private Claims tokenInRedis(String refreshRedisKey, String token) {
        String tokenInRedis = stringRedisTemplate.opsForValue().get(refreshRedisKey);
        SecretKey key = getKeyForToday();
        Claims refreshClaims = Jwts.parserBuilder()
                .setSigningKey(key)  // 你生成 token 時用的密鑰
                .build()
                .parseClaimsJws(tokenInRedis)
                .getBody();

        String username = refreshClaims.getSubject();
        String usernameAccessJwtId = refreshClaims.getId();
        String blacklistRedisKey = RedisKey.redisKey.get("blacklist").replace("{1}", usernameAccessJwtId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistRedisKey))) {
            logger.error("{} : Token 已被撤銷", username);
            throw new RuntimeException(username + " - Token 已被撤銷");
        }
        return Jwts.parserBuilder()
                .setSigningKey(key)  // 你生成 token 時用的密鑰
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public ResponseEntity<?> unread(NotificationsRequset request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("unread 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (unread) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(unread)使用者錯誤");
                            throw new RuntimeException(username + " - (unread)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (unread)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (unread) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (unread)查使用者帳號不存在", username);
                            throw new ResourceNotFoundException(username + " - (unread)查使用者帳號不存在");
                        }

                        List<String> selectRoles = roleMapper.getSelectRoles(username);
                        String allowedRoles = Permissions.ORDERBACKEND_ITEM_TOKEN.getPermission();
                        boolean hasRole = selectRoles.stream().anyMatch(role -> role.equals(allowedRoles));

                        List<Map<String, Object>> data = new ArrayList<>();
                        List<Map<String, Object>> listAll = new ArrayList<>();
                        if (hasRole) {
                            // orderbackend
                            List<Map<String, Object>> getUserdataDetails = notificationsMapper.selectUserdataDetails();
                            if (!getUserdataDetails.isEmpty()) {
                                listAll.addAll(getUserdataDetails);
                            }
                            data.add(Map.of("details", "\uD83D\uDD14 通知(" + listAll.size() + ")"));
                            data.add(Map.of("cnt", listAll.size()));
                            Map<String, Object> dataMap = new TreeMap<>();
                            for (int i = 0; i < listAll.size(); i++) {
                                dataMap.put("details" + (i), listAll.get(i).get("title"));
                                data.add(dataMap);
                            }
                        } else {
                            // user
                            List<Map<String, Object>> getQuotations = notificationsMapper.selectQuotations(userData);
                            if (!getQuotations.isEmpty()) {
                                for (Map<String, Object> map : getQuotations) {
                                    String status = map.get("status").toString();
                                    map.put("status", "狀態:" + StatusKey.quotationsStatusKey.get(status));
                                }
                                listAll.addAll(getQuotations);
                            }
                            data.add(Map.of("details", "\uD83D\uDD14 通知(" + listAll.size() + ")"));
                            data.add(Map.of("cnt", listAll.size()));
                            Map<String, Object> dataMap = new TreeMap<>();
                            for (int i = 0; i < listAll.size(); i++) {
                                dataMap.put("status" + (i), listAll.get(i).get("status"));
                                dataMap.put("details" + (i), listAll.get(i).get("title"));
                                data.add(dataMap);
                            }
                        }
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (unread)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : 取Token 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 取Token，資源忙碌，請重試"
                );
                List<Map<String, Object>> data = List.of(Map.of("1", messageList));
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                data
                        ));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
