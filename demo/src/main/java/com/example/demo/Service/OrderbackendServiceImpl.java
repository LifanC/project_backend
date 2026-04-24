package com.example.demo.Service;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.*;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.User.*;
import com.example.demo.Exception.*;
import com.example.demo.Mapper.OrderbackendMapper;
import com.example.demo.Mapper.ProductMapper;
import com.example.demo.Mapper.SecretMapper;
import com.example.demo.Mapper.UserMapper;
import com.example.demo.Security.Annotation.CheckRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class OrderbackendServiceImpl implements OrderbackendService {

    private final Logger logger = LoggerFactory.getLogger(OrderbackendServiceImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderbackendMapper orderbackendMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public OrderbackendServiceImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            ProductMapper productMapper,
            OrderbackendMapper orderbackendMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.orderbackendMapper = orderbackendMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /*
     * 防 Cache Stampede（雪崩）
     * 問題：* 大量 key 同時過期 → DB 被打爆
     * */
    private int expirationSecondsAddRndomNumber() {
        int min = 1;
        int max = 60;
        return Math.toIntExact(expirationSeconds + (new Random().nextInt((max - min) + 1) + min));
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

    private Map<String, Object> getDetailsData(UserData userData) {
        return orderbackendMapper.selectDetailsData(userData).get(userData.getUsername());
    }

    private List<Map<String, Object>> getQuotationsData(UserData userData) {
        return orderbackendMapper.selectQuotationsData(userData);
    }

    private Map<String, Object> getQuotationsDataSend(UserDataSend userDataSend) {
        return orderbackendMapper.selectQuotationsDataSend(userDataSend).get(userDataSend.getUsername());
    }

    @Override
    public ResponseEntity<?> testLogin() {
        logger.info("orderbackend/testLogin: orderbackend is working!");
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> dataMap = new TreeMap<>();
        dataMap.put("1", List.of("orderbackend is working!"));
        data.add(dataMap);
        HttpStatus status = HttpStatus.OK;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        data
                ));
    }

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> takeToken(UserRequest request) {
        final String username = request.getUsername();
        final String password = request.getPassword();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User takeToken 拿鎖");
                try {
                    // 最多失敗嘗試次數
                    final int maxFailAttempts = 5;
                    // failKey TTL（同上，避免混亂）
                    final long failExpireSeconds = expirationSeconds;
                    // lockKey TTL（鎖 ? 秒）
                    final long lockSeconds = 60;
                    String lockKey = RedisKey.redisKey.get("lock").replace("{1}", username);
                    String failKey = RedisKey.redisKey.get("fail").replace("{1}", username);
                    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
                        Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
                        logger.error("{} : 連續錯誤{}次，帳號暫時被鎖，請稍後再試({}秒)", username, maxFailAttempts, ttl);
                        throw new RuntimeException(username + " - 連續錯誤" + maxFailAttempts + "次，帳號暫時被鎖，請稍後再試(" + ttl + "秒)");
                    }

                    Map<String, Object> userSelect;
                    String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                    String json = stringRedisTemplate.opsForValue().get(userOnly);
                    if (json != null) {
                        userSelect = objectMapper.readValue(json, new TypeReference<>() {
                        });
                    } else {
                        userSelect = getUserData(userData);
                        String jsonMap = objectMapper.writeValueAsString(userSelect);
                        stringRedisTemplate.opsForValue().set(
                                userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }
                    if (userSelect == null) {
                        logger.error("{} : (Token 取得)帳號不存在", username);
                        throw new ResourceNotFoundException(username + " - 帳號不存在");
                    }
                    final String userPassword = userSelect.get("password").toString();
                    final String permissions = userSelect.get("permissions").toString();
                    if (!passwordEncoder.matches(password, userPassword)) {
                        Long failCount = stringRedisTemplate.opsForValue().increment(failKey);
                        // 第一次失敗才設 TTL
                        if (failCount != null && failCount == 1) {
                            stringRedisTemplate.expire(failKey, failExpireSeconds, TimeUnit.SECONDS);
                        }
                        if (failCount != null && failCount >= maxFailAttempts) {
                            stringRedisTemplate.opsForValue().set(
                                    lockKey,
                                    "1",
                                    lockSeconds,
                                    TimeUnit.SECONDS
                            );
                        }
                        logger.error("{} : (Token 取得)帳號密碼錯誤，第{}次。共可輸入{}次", username, failCount, maxFailAttempts);
                        throw new ResourceNotFoundException(username + " - 帳號密碼錯誤，第" + failCount + "次。共可輸入" + maxFailAttempts + "次");
                    }
                    stringRedisTemplate.delete(failKey);
                    stringRedisTemplate.delete(lockKey);

                    // JWT 簽名與驗證用的「祕密字串（secret）」
                    final String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                    String jti = UUID.randomUUID().toString();
                    int expirationSecondsAddRndomNumber = expirationSecondsAddRndomNumber();
                    final String refreshToken = Jwts.builder()
                            .setId(jti)
                            .setSubject(username)
                            .setIssuedAt(new Date())
                            .setExpiration(
                                    Date.from(
                                            Instant.now().plus(expirationSecondsAddRndomNumber, ChronoUnit.SECONDS)
                                    )
                            )
                            .signWith(getKeyForToday())
                            .compact();
                    final Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                            refreshRedisKey,
                            refreshToken,
                            expirationSecondsAddRndomNumber,
                            TimeUnit.SECONDS
                    );
                    if (!success) {
                        throw new IllegalStateException(username + " - Token 已經存在");
                    }
                    logger.info("{} : (Token 取得)成功", username);
                    List<Object> messageList = List.of(
                            "帳號 - " + username,
                            "權限 - " + permissions,
                            username + " - Token 取得成功",
                            ConvertFormat.time("")
                    );
                    List<Map<String, Object>> data = new ArrayList<>();
                    Map<String, Object> dataMap = new TreeMap<>();
                    dataMap.put("1", messageList);
                    data.add(dataMap);
                    HttpStatus status = HttpStatus.OK;
                    return ResponseEntity
                            .status(status)
                            .body(ApiResponse.api(
                                    status,
                                    data
                            ));
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> validate(UserTokenValidateRequest request) {
        final String username = request.getUsername();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User validate 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (Token驗證)不存在或已過期，請重新取得 Token", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期，請重新取得 Token");
                        }
                        String refreshTokenInRedis = stringRedisTemplate.opsForValue().get(refreshRedisKey);
                        Claims claims = Jwts.parserBuilder()
                                .setSigningKey(getKeyForToday())  // 你生成 token 時用的密鑰
                                .build()
                                .parseClaimsJws(refreshTokenInRedis)
                                .getBody();
                        String usernameJwt = claims.getSubject();
                        String usernameJwtId = claims.getId();
                        logger.info("{} : (Token驗證)有效的 JWT token", usernameJwt);

                        String blacklistRedisKey = RedisKey.redisKey.get("blacklist").replace("{1}", usernameJwtId);
                        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistRedisKey))) {
                            logger.error("{} : (Token驗證)Token 已被撤銷", username);
                            throw new RuntimeException(username + " - Token 已被撤銷");
                        }

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (Token驗證)帳號不存在", username);
                            throw new ResourceNotFoundException(username + " - 帳號不存在");
                        }
                        // JWT 簽名與驗證用的「祕密字串（secret）」
                        final String permissions = userSelect.get("permissions").toString();
                        String jti = UUID.randomUUID().toString();
                        int expirationSecondsAddRndomNumber = expirationSecondsAddRndomNumber();
                        String accessToken = Jwts.builder()
                                .setId(jti)
                                .setSubject(usernameJwt)
                                .claim("roles", permissions)
                                .setIssuedAt(new Date())
                                .setExpiration(
                                        Date.from(
                                                Instant.now().plus(expirationSecondsAddRndomNumber, ChronoUnit.SECONDS)
                                        )
                                )
                                .signWith(getKeyForToday())
                                .compact();
                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", "*")
                                .replace("{2}", usernameJwt);
                        // 避免 Redis key 無限制增加導致記憶體耗盡
                        int cnt = 5;
                        ScanOptions options = ScanOptions.scanOptions()
                                .match(accessRedisKey)
                                .count(cnt)
                                .build();
                        // redis(指定key)的數量
                        Long redisCount =
                                stringRedisTemplate.execute((RedisCallback<Long>) connection -> {
                                    long count = 0;
                                    try (Cursor<byte[]> cursor = connection.scan(options)) {
                                        while (cursor.hasNext()) {
                                            cursor.next();
                                            count++;
                                        }
                                    }
                                    return count;
                                });
                        redisCount = redisCount == null ? 0L : redisCount;
                        // redis(指定key)的數量，超過 ? 就全清
                        // 上限數量
                        int maximumQuantity = 20;
                        if (redisCount >= maximumQuantity) {
                            // Redis「我希望每次 SCAN 返回大約 5 個 key」
                            // 這是一個 建議值，Redis 可能返回多於或少於這個數量，取決於內部算法。
                            redisDels(accessRedisKey, cnt);
                        }
                        accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", jti)
                                .replace("{2}", usernameJwt);
                        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                                accessRedisKey,
                                accessToken,
                                expirationSecondsAddRndomNumber,
                                TimeUnit.SECONDS
                        );
                        if (!success) {
                            logger.info("{} : (Token驗證)已經存在", usernameJwt);
                        } else {
                            logger.info("{} : (Token驗證)不存在", usernameJwt);
                        }
                        logger.info("{} : (Token驗證)成功", usernameJwt);
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + permissions,
                                username + " - Token 驗證成功",
                                ConvertFormat.time("")
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        dataMap.put("2", accessToken);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (Token驗證)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : validate 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - Token驗證，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> logout(QueryUserRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User logout 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (Token登出)不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        String refreshTokenInRedis = stringRedisTemplate.opsForValue().get(refreshRedisKey);
                        SecretKey keyForToday = getKeyForToday();

                        Claims refreshClaims = Jwts.parserBuilder()
                                .setSigningKey(keyForToday)  // 你生成 token 時用的密鑰
                                .build()
                                .parseClaimsJws(refreshTokenInRedis)
                                .getBody();

                        String usernameAccessJwtId = refreshClaims.getId();
                        String blacklistRedisKey = RedisKey.redisKey.get("blacklist").replace("{1}", usernameAccessJwtId);
                        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistRedisKey))) {
                            logger.error("{} : (Token登出)Token 已被撤銷", username);
                            throw new RuntimeException(username + " - Token 已被撤銷");
                        }

                        long remainingSeconds = stringRedisTemplate.getExpire(refreshRedisKey, TimeUnit.SECONDS);
                        stringRedisTemplate.opsForValue().set(
                                blacklistRedisKey,
                                usernameAccessJwtId,
                                remainingSeconds,
                                TimeUnit.SECONDS
                        );

                        Boolean refreshExisted = stringRedisTemplate.delete(refreshRedisKey);
                        logger.info("{} : refresh Token {}",
                                username,
                                Boolean.TRUE.equals(refreshExisted)
                                        ? " : 成功登出 Token 已刪除"
                                        : " : 本來就不存在或已過期");

                        Claims accessClaims = Jwts.parserBuilder()
                                .setSigningKey(keyForToday)  // 你生成 token 時用的密鑰
                                .build()
                                .parseClaimsJws(token)
                                .getBody();
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(Token登出)使用者錯誤");
                            throw new RuntimeException(username + " - 使用者錯誤");
                        }

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", "*")
                                .replace("{2}", usernameAccessJwt);
                        stringRedisTemplate.delete(accessRedisKey);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : 登出 Token 帳號不存在", username);
                            throw new ResourceNotFoundException(username + " - 登出 Token 帳號不存在");
                        }
                        String permissions = userSelect.get("permissions").toString();
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + permissions,
                                username + " - Token 已登出",
                                ConvertFormat.time("")
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        dataMap.put("2", "");
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (Token登出)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : logout 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - Token登出，資源忙碌，請重試"
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

    private void redisDels(String accessRedisKey, int cnt) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(accessRedisKey)
                .count(cnt)
                .build();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                List<String> keysToDelete = new ArrayList<>();
                while (cursor.hasNext()) {
                    String key = new String(cursor.next());
                    keysToDelete.add(key);
                    // 批量刪除：每 ? 個 key 刪一次，避免一次性刪太多
                    if (keysToDelete.size() >= cnt) {
                        stringRedisTemplate.delete(keysToDelete);
                        keysToDelete.clear();
                    }
                }
                // 刪除剩下的
                if (!keysToDelete.isEmpty()) {
                    stringRedisTemplate.delete(keysToDelete);
                }
            }
            return null;
        });
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

    private List<Map<String, Object>> getProduct(Product product) {
        return productMapper.select(product);
    }

    @Override
    @Transactional
    @CheckRole(Permissions.USER_ITEM_QUERY)
    public ResponseEntity<?> queryUser(QueryUserRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User queryUser 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢使用者名單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢使用者名單)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢使用者名單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢使用者名單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢使用者名單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢使用者名單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : 查使用者帳號不存在", username);
                            throw new ResourceNotFoundException(username + " - 查使用者帳號不存在");
                        }
                        String permissions = userSelect.get("permissions").toString();
                        List<String> isUserName = userMapper.queryUserName();
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + permissions,
                                username + " - 查詢使用者名單",
                                isUserName,
                                "新增日期 - " + ConvertFormat.time(userSelect.get("created_date").toString()),
                                "更改日期 - " + ConvertFormat.time(userSelect.get("updated_date").toString())
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (查詢使用者名單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : queryUser 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 查詢使用者名單，資源忙碌，請重試"
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

    /*
     * 利潤率 × 售價 = 售價 - 成本
     * 利潤率 × 售價 - 售價 = -成本
     * 售價 × (利潤率 - 1) = -成本
     * 售價 × (1 - 利潤率) = 成本
     * 售價 = 成本 ÷ (1 - 利潤率)
     * */
    // 利潤率 = (售價 - 成本) ÷ 售價
    private Map<String, BigDecimal> calculateByMargin(BigDecimal cost, String marginPercent) {
        Map<String, BigDecimal> map = new HashMap<>();

        // 1. 先算售價
        BigDecimal rate = BigDecimal.ONE
                .subtract(
                        (new BigDecimal(marginPercent)
                                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP))
                );
        BigDecimal sellingPrice = cost.divide(rate, 0, RoundingMode.HALF_UP);

        // 2. 算利潤
        BigDecimal profit = sellingPrice.subtract(cost).setScale(0, RoundingMode.HALF_UP);

        // 3. 算實際利潤率（再驗證一次）
        BigDecimal margin = profit
                .divide(sellingPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP);

        map.put("sellingPrice", sellingPrice);
        map.put("profit", profit);
        map.put("margin", margin);
        return map;
    }

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> quotationsProductItem(QuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String userPercent = request.getUserPercent();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend quotationsProductItem 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (用戶商品報價) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(用戶商品報價)使用者錯誤");
                            throw new RuntimeException(username + " - (用戶商品報價)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (用戶商品報價)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(用戶商品報價)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (用戶商品報價) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (用戶商品報價) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (用戶商品報價) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        for (Map<String, Object> user : getUserUser) {
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("訂單明細 - 帳號 - " + user.get("username").toString());
                            String order_item = user.get("order_item").toString();
                            String[] order_items = order_item.split(",");
                            for (String item : order_items) {
                                String[] arr = item.split(":");
                                List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                messageGroup.add("---------------------------------------");
                                messageGroup.add("商品編號 - " + arr[0]);
                                messageGroup.add("商品名稱 - " + productsSelect.getFirst().get("products_name").toString());
                                BigDecimal A = new BigDecimal(arr[1]);
                                BigDecimal B = new BigDecimal(productsSelect.getFirst().get("stock").toString());
                                BigDecimal C = B.subtract(A);
                                messageGroup.add(C.compareTo(BigDecimal.ZERO) < 0 ? "庫存不夠" + C.abs() + "筆" : "庫存足夠");
                                BigDecimal price = new BigDecimal(productsSelect.getFirst().get("price").toString());
                                messageGroup.add("訂購數量 - " + A);
                                messageGroup.add("商品庫存量: " + B);
                                messageGroup.add("價格 - " + price);
                                String num = userPercent;
                                Map<String, BigDecimal> queryQuotationsMap = calculateByMargin(price, num);
                                messageGroup.add("┌-----" + num + "% -----┐");
                                messageGroup.add("|售價: " + queryQuotationsMap.get("sellingPrice").toString());
                                messageGroup.add("|利潤: " + queryQuotationsMap.get("profit").toString());
                                messageGroup.add("|利潤率: " + queryQuotationsMap.get("margin").toString() + "%");
                                messageGroup.add("└-----" + num + "% -----┘");
                                messageGroup.add("描述 - " + productsSelect.getFirst().get("description").toString());
                                messageGroup.add("---------------------------------------");
                            }
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "用戶商品報價",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (用戶商品報價)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : quotationsProductItem 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 用戶商品報價，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> confirmQuotationsProductItem(QuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String userPercent = request.getUserPercent();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend confirmQuotationsProductItem 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (確認報價) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(確認報價)使用者錯誤");
                            throw new RuntimeException(username + " - (確認報價)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (確認報價)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(確認報價)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (確認報價) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (確認報價) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (確認報價) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        UserData userDataDetails = new UserData(useruser);
                        List<Object> productsList = new ArrayList<>();
                        Map<String, Object> detailsData = getDetailsData(userDataDetails);
                        logger.info("用戶編號: {}", useruser);
                        logger.info("銷售 % 數: {}", userPercent);
                        productsList.add("用戶編號: " + useruser);
                        productsList.add("銷售 % 數: " + userPercent);
                        String order_item = detailsData.get("order_item").toString();
                        String[] order_items = order_item.split(",");
                        Integer quotationsMax = orderbackendMapper.selectQuotationsMax();
                        BigDecimal decimal = new BigDecimal(String.valueOf(quotationsMax + 1));

                        List<Boolean> atLastJudgesList = new ArrayList<>();
                        List<Object> msg = new ArrayList<>();
                        for (int i = 0; i < order_items.length; i++) {
                            String item = order_items[i];
                            String[] arr = item.split(":");
                            String userUserProductsId = arr[0];
                            List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(userUserProductsId)));
                            BigDecimal product_id = new BigDecimal(productsSelect.getFirst().get("product_id").toString());
                            String products_name = productsSelect.getFirst().get("products_name").toString();
                            BigDecimal stock = new BigDecimal(productsSelect.getFirst().get("stock").toString());
                            boolean judge = stock.subtract(new BigDecimal(arr[1])).compareTo(BigDecimal.ZERO) < 0;
                            if (judge) {
                                logger.info("{}:{}:庫存量不夠", product_id, products_name);
                                msg.add(product_id + ":" + products_name + ":庫存量不夠");
                            } else {
                                logger.info("{}:{}:庫存量足夠", product_id, products_name);
                                msg.add(product_id + ":" + products_name + ":庫存量足夠");
                            }
                            atLastJudgesList.add(i, judge);
                        }
                        // 只要其中有一項庫存量不足就不存入DB
                        boolean hasTrue = atLastJudgesList.stream().anyMatch(Boolean.TRUE::equals);
                        if (hasTrue) {
                            productsList.add(msg);
                        } else {
                            try {
                                BigDecimal totalPrice = BigDecimal.ZERO;
                                for (int i = 0; i < order_items.length; i++) {
                                    String item = order_items[i];
                                    String[] arr = item.split(":");
                                    String userUserProductsId = arr[0];
                                    String userUserQuantity = arr[1];
                                    BigDecimal quantity = new BigDecimal(userUserQuantity);
                                    List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(userUserProductsId)));
                                    BigDecimal product_id = new BigDecimal(productsSelect.getFirst().get("product_id").toString());
                                    BigDecimal price = new BigDecimal(productsSelect.getFirst().get("price").toString());
                                    String num = userPercent;
                                    Map<String, BigDecimal> queryQuotationsMap = calculateByMargin(price, num);
                                    BigDecimal sellingPrice = new BigDecimal(queryQuotationsMap.get("sellingPrice").toString());
                                    BigDecimal total = sellingPrice.multiply(quantity);
                                    totalPrice = totalPrice.add(total);
                                    logger.info("用戶訂單: {}", item);
                                    logger.info("用戶商品編號: {}", userUserProductsId);
                                    logger.info("用戶商品數量: {}", userUserQuantity);
                                    logger.info("用戶商品銷售價格: {}", sellingPrice);
                                    logger.info("用戶商品價格: {}", price);
                                    logger.info("用戶商品銷售合計: {}", total);
                                    logger.info("用戶商品單品價格: {}", price);
                                    List<Object> messageGroup = new ArrayList<>();
                                    messageGroup.add("┌----------第" + (i + 1) + "筆----------┐");
                                    messageGroup.add("用戶商品數量: " + userUserQuantity);
                                    messageGroup.add("用戶商品單品價格: " + price);
                                    messageGroup.add("用戶商品銷售單品價格: " + sellingPrice);
                                    messageGroup.add("用戶商品銷售合計: " + total);
                                    messageGroup.add("└----------第" + (i + 1) + "筆----------┘");
                                    productsList.add(messageGroup);
                                    QuotationItems quotationItems = new QuotationItems(decimal, product_id);
                                    quotationItems.setQuantity(quantity);
                                    quotationItems.setPrice(sellingPrice);
                                    quotationItems.setUnit_percent(new BigDecimal(userPercent));
                                    quotationItems.setUnit_price(price);
                                    orderbackendMapper.createQuotationItems(quotationItems);
                                }
                                Quotations quotations = new Quotations(decimal);
                                quotations.setUsername(useruser);
                                quotations.setStatus(Backend.STATUS_QUOTATIONS_ESTIMATE.getBackend());
                                quotations.setTotal_price(totalPrice);
                                orderbackendMapper.createQuotations(quotations);
                            } catch (DataIntegrityViolationException e) {
                                logger.warn("confirmQuotationsProductItem 新增報價資料不合法，username={}", username);
                                throw new IsViolationException(username + " - 新增報價資料不合法", e);
                            } catch (DataAccessException e) {
                                logger.error("confirmQuotationsProductItem 資料庫錯誤，username={}", username);
                                throw new DBException(username + " - 系統錯誤，請稍後再試", e);
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "確認報價單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (確認報價)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : confirmQuotationsProductItem 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 確認報價單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> deleteQuotationsProduct(DeleteQuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend deleteQuotationsProduct 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (刪除報價) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(刪除報價)使用者錯誤");
                            throw new RuntimeException(username + " - (刪除報價)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (刪除報價)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(刪除報價)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (刪除報價) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (刪除報價) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (刪除報價) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        UserData userDataDetails = new UserData(useruser);
                        List<Map<String, Object>> quotationsData = getQuotationsData(userDataDetails);
                        productsList.add("報價單");
                        for (Map<String, Object> quotationData : quotationsData) {
                            BigDecimal quotation_idDel = new BigDecimal(quotationData.get("quotation_id").toString());
                            String usernameDel = quotationData.get("username").toString();
                            String statusDel = quotationData.get("status").toString();
                            StringBuilder msg = new StringBuilder();
                            msg.append("編號").append(quotation_idDel).append(":用戶帳號:").append(usernameDel);
                            if (Backend.STATUS_QUOTATIONS_ESTIMATE.getBackend().equals(statusDel)) {
                                QuotationItems quotationItems = new QuotationItems(quotation_idDel, null);
                                orderbackendMapper.delQuotationItems(quotationItems);
                                Quotations quotations = new Quotations(quotation_idDel);
                                quotations.setUsername(usernameDel);
                                orderbackendMapper.delQuotations(quotations);
                                msg.append(":報價單刪除成功");
                            } else {
                                msg.append(":報價已送出無法刪除");
                            }
                            productsList.add(msg);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "刪除報價單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (刪除報價)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : deleteQuotationsProduct 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 刪除報價單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> queryQuotationsProduct(QueryQuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend queryQuotationsProduct 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢報價單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢報價單)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢報價單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢報價單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢報價單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢報價單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (查詢報價單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (查詢報價單) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        UserData userDataDetails = new UserData(useruser);
                        List<Map<String, Object>> quotationsData =
                                orderbackendMapper.quotationsItemsProductsData(userDataDetails);
                        Map<String, List<Map<String, Object>>> groupListAll = new TreeMap<>();
                        for (Map<String, Object> quotationData : quotationsData) {
                            String quotationId = quotationData.get("quotation_id").toString();
                            groupListAll
                                    .computeIfAbsent(quotationId, k -> new ArrayList<>())
                                    .add(quotationData);
                        }
                        Map<String, List<Map<String, Object>>> sorte = groupListAll.entrySet()
                                .stream()
                                .sorted(Map.Entry.<String, List<Map<String, Object>>>comparingByKey().reversed())
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (e1, e2) -> e1,
                                        LinkedHashMap::new
                                ));
                        for (Map.Entry<String, List<Map<String, Object>>> entry : sorte.entrySet()) {
                            String key = entry.getKey();
                            List<Map<String, Object>> list = entry.getValue();
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("報價單編號: " + key);
                            for (int i = 0; i < list.size(); i++) {
                                Map<String, Object> quotationData = list.get(i);
                                String statusQuery = quotationData.get("status").toString();
                                BigDecimal quantityQuery = new BigDecimal(quotationData.get("quantity").toString());
                                BigDecimal priceQuery = new BigDecimal(quotationData.get("price").toString());
                                BigDecimal sumPriceQuery = new BigDecimal(quotationData.get("sum_price").toString());
                                String productsNameQuery = quotationData.get("products_name").toString();
                                String descriptionQuery = quotationData.get("description").toString();
                                messageGroup.add("|-----第" + (i + 1) + "筆-----|");
                                messageGroup.add("|報價單:商品");
                                messageGroup.add("|用戶名稱: " + useruser);
                                // estimate（預估） / sent（已送出） / accepted（客戶接受） / rejected（拒絕）
                                messageGroup.add("|狀態: " + StatusKey.quotationsStatusKey.get(statusQuery));
                                messageGroup.add("|數量: " + quantityQuery);
                                messageGroup.add("|價格: " + priceQuery);
                                messageGroup.add("|合計: " + sumPriceQuery);
                                messageGroup.add("|名稱: " + productsNameQuery);
                                messageGroup.add("|敘述: " + descriptionQuery);
                            }
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "查詢報價單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (查詢報價單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : queryQuotationsProduct 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 查詢報價單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> sendQuotationsProduct(SendQuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String userUserQuotationsId = request.getUserUserQuotationsId().trim();
        boolean isNumber = userUserQuotationsId.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (送出報價單)商品編號只能包含數字", username);
            throw new BadRequestException(username + " - (送出報價單)商品編號只能包含數字");
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend sendQuotationsProduct 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (送出報價單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(送出報價單)使用者錯誤");
                            throw new RuntimeException(username + " - (送出報價單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (送出報價單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(送出報價單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (送出報價單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (送出報價單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (送出報價單) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        UserDataSend userDataDetails = new UserDataSend(useruser);
                        String send = Backend.STATUS_QUOTATIONS_SENT.getBackend();
                        userDataDetails.setStatus(send);
                        userDataDetails.setQuotation_id(new BigDecimal(userUserQuotationsId));
                        Map<String, Object> quotationsDataSend = getQuotationsDataSend(userDataDetails);
                        if (quotationsDataSend == null) {
                            productsList.add("報價單編號:" + userUserQuotationsId + ":送出失敗，無此報價單");
                        } else {
                            String statusSend = quotationsDataSend.get("status").toString();
                            if (Backend.STATUS_QUOTATIONS_ESTIMATE.getBackend().equals(statusSend)) {
                                orderbackendMapper.updateQuotations(userDataDetails);
                                productsList.add("報價單編號:" + userUserQuotationsId + ":送出成功");
                                productsList.add("報價單狀態:" + StatusKey.quotationsStatusKey.get(send));
                            } else {
                                productsList.add("報價單編號:" + userUserQuotationsId + ":已送出，勿重複送單");
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "送出報價單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (送出報價單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : sendQuotationsProduct 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 送出報價單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> ordersUser(OrdersUserItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend ordersUser 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢用戶訂單名單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢用戶訂單名單)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢用戶訂單名單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢用戶訂單名單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢用戶訂單名單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢用戶訂單名單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (查詢用戶訂單名單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Orders orders = new Orders();
                        orders.setQuotationsStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()
                                )
                        );
                        orders.setStatuss(
                                List.of(
                                        Backend.STATUS_ORDERS_PENDING.getBackend(),
                                        Backend.STATUS_ORDERS_CONFIRMED.getBackend(),
                                        Backend.STATUS_ORDERS_CANCELLED.getBackend()
                                )
                        );
                        List<Map<String, Object>> ordersData = orderbackendMapper.selectOrdersData(orders);
                        if (ordersData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotation_id = ordersData.get(i).get("quotation_id").toString();
                                String status = ordersData.get(i).get("status").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                List<Object> messageGroup = new ArrayList<>();
                                messageGroup.add("第" + (i + 1) + "筆");
                                messageGroup.add("訂單編號:" + order_id + ":用戶:" + quotationsUsername);
                                messageGroup.add("報價單編號:" + quotation_id);
                                messageGroup.add("狀態:" + StatusKey.ordersStatusKey.get(status));
                                productsList.add(messageGroup);
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "查詢用戶訂單名單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (查詢用戶訂單名單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : ordersUser 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 查詢用戶訂單名單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> ordersProduct(OrdersProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String orderId = request.getOrderId().trim();
        if (StringUtils.hasText(orderId)) {
            boolean isNumber = orderId.matches("^\\d+$");
            if (!isNumber) {
                logger.error("{} - (訂單)訂單編號只能包含數字", username);
                throw new BadRequestException(username + " - (訂單)訂單編號只能包含數字");
            }
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend ordersProduct 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (訂單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(訂單)使用者錯誤");
                            throw new RuntimeException(username + " - (訂單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (訂單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(訂單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (訂單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (訂單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (訂單) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Orders orders = new Orders(StringUtils.hasText(orderId) ? new BigDecimal(orderId) : null);
                        orders.setUsername(useruser);
                        orders.setQuotationsStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()
                                )
                        );
                        orders.setStatuss(
                                List.of(
                                        Backend.STATUS_ORDERS_PENDING.getBackend(),
                                        Backend.STATUS_ORDERS_CONFIRMED.getBackend(),
                                        Backend.STATUS_ORDERS_CANCELLED.getBackend()
                                )
                        );
                        List<Map<String, Object>> ordersData = orderbackendMapper.selectOrdersData(orders);
                        if (ordersData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotation_id = ordersData.get(i).get("quotation_id").toString();
                                String status = ordersData.get(i).get("status").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                List<Object> messageGroup = new ArrayList<>();
                                messageGroup.add("第" + (i + 1) + "筆");
                                messageGroup.add("訂單編號:" + order_id + ":用戶:" + quotationsUsername);
                                messageGroup.add("報價單編號:" + quotation_id);
                                messageGroup.add("狀態:" + StatusKey.ordersStatusKey.get(status));
                                productsList.add(messageGroup);
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "訂單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (訂單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : ordersProduct 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 訂單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> ordersConfirmed(OrdersConfirmedCancelledItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String orderId = request.getOrderId().trim();
        boolean isNumber = orderId.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (確認訂單)訂單編號只能包含數字", username);
            throw new BadRequestException(username + " - (確認訂單)訂單編號只能包含數字");
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend ordersConfirmed 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (確認訂單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(確認訂單)使用者錯誤");
                            throw new RuntimeException(username + " - (確認訂單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (確認訂單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(確認訂單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (確認訂單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (確認訂單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (確認訂單) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Orders orders = new Orders(new BigDecimal(orderId));
                        orders.setUsername(useruser);
                        orders.setQuotationsStatuss(List.of(Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()));
                        orders.setStatuss(List.of(Backend.STATUS_ORDERS_PENDING.getBackend()));
                        List<Map<String, Object>> ordersData = orderbackendMapper.selectOrdersData(orders);
                        if (ordersData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String confirmed = Backend.STATUS_ORDERS_CONFIRMED.getBackend();
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                List<Object> messageGroup = new ArrayList<>();
                                messageGroup.add("第" + (i + 1) + "筆");
                                messageGroup.add("編號:" + order_id + ":用戶:" + quotationsUsername);
                                messageGroup.add("狀態:" + StatusKey.ordersStatusKey.get(confirmed));
                                productsList.add(messageGroup);
                            }
                            orders.setStatus(confirmed);
                            orderbackendMapper.updateOrders(orders);

                            // *出貨（shipments）
                            /*
                             * TW = 地區
                             * 日期 = 20260412
                             * 流水號 = 001
                             * TW20260412001(追蹤號碼)
                             * */
                            String prefix = "TW";
                            LocalDate today = LocalDate.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                            String datePart = today.format(formatter);
                            Shipments shipments = new Shipments(new BigDecimal(orderId));
                            String preparing = Backend.STATUS_SHIPMENTS_PENDING.getBackend();
                            shipments.setStatus(preparing);
                            shipments.setPrefix(prefix);
                            shipments.setDate_part(datePart);
                            String serial = String.format("%03d", orderbackendMapper.serialMax(shipments));
                            shipments.setSerial(serial);
                            String trackingNumber = prefix + datePart + serial;
                            shipments.setTracking_number(trackingNumber);
                            orderbackendMapper.createShipments(shipments);
                            productsList.add("出貨狀態:" + StatusKey.shipmentsStatusKey.get(preparing));

                            // *付款（payments）
                            Payments payments = new Payments(new BigDecimal(orderId));
                            String unpaid = Backend.STATUS_PAYMENTS_UNPAID.getBackend();
                            String cash = Backend.METHOD_PAYMENTS_CASH.getBackend();
                            payments.setAmount(BigDecimal.ZERO);
                            payments.setStatus(unpaid);
                            payments.setPayments_method(cash);
                            orderbackendMapper.createPayments(payments);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "確認訂單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (確認訂單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : ordersConfirmed 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 確認訂單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> ordersCancelled(OrdersConfirmedCancelledItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String orderId = request.getOrderId().trim();
        boolean isNumber = orderId.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (取消訂單)訂單編號只能包含數字", username);
            throw new BadRequestException(username + " - (取消訂單)訂單編號只能包含數字");
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend ordersCancelled 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (取消訂單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(取消訂單)使用者錯誤");
                            throw new RuntimeException(username + " - (取消訂單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (取消訂單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(取消訂單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (取消訂單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (取消訂單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (取消訂單) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Orders orders = new Orders(new BigDecimal(orderId));
                        orders.setUsername(useruser);
                        orders.setQuotationsStatuss(List.of(Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()));
                        orders.setStatuss(List.of(Backend.STATUS_ORDERS_PENDING.getBackend()));
                        List<Map<String, Object>> ordersData = orderbackendMapper.selectOrdersData(orders);
                        if (ordersData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String cancelled = Backend.STATUS_ORDERS_CANCELLED.getBackend();
                            orders.setStatus(cancelled);
                            orderbackendMapper.updateOrders(orders);
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                List<Object> messageGroup = new ArrayList<>();
                                messageGroup.add("第" + (i + 1) + "筆");
                                messageGroup.add("編號:" + order_id + ":用戶:" + quotationsUsername);
                                messageGroup.add("狀態:" + StatusKey.ordersStatusKey.get(cancelled));
                                productsList.add(messageGroup);
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "取消訂單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (取消訂單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : ordersCancelled 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 取消訂單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> shipmentsTrackingNumber(ShipmentsItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String orderId = request.getOrderId();
        final String trackingNumber = request.getTrackingNumber().trim().toUpperCase();
        final String datePart = request.getDatePart().trim();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend shipmentsTrackingNumber 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢用戶出貨名單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢用戶出貨名單)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢用戶出貨名單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢用戶出貨名單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢用戶出貨名單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢用戶出貨名單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (查詢用戶出貨名單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        if (StringUtils.hasText(useruser)) {
                            logger.info("(查詢用戶出貨名單)用戶帳號 - {}", useruser);
                            UserUser userUser = new UserUser(useruser);
                            List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                            if (getUserUser.isEmpty()) {
                                logger.error("{} : (查詢用戶出貨名單) 用戶不存在", useruser);
                                throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                            }
                        }
                        BigDecimal ordersIdBigDecimal = null;
                        if (StringUtils.hasText(orderId)) {
                            logger.info("(查詢用戶出貨名單)出貨單編號 - {}", orderId);
                            boolean isNumber = orderId.matches("^\\d+$");
                            if (!isNumber) {
                                logger.error("{} - (查詢用戶出貨名單)出貨編號只能包含數字", orderId);
                                throw new BadRequestException(orderId + " - (查詢用戶出貨名單)出貨編號只能包含數字");
                            }
                            ordersIdBigDecimal = new BigDecimal(orderId);
                        }
                        if (StringUtils.hasText(trackingNumber)) {
                            logger.info("(查詢用戶出貨名單)追蹤號碼(13碼) - {}", trackingNumber);
                            boolean isOk = trackingNumber.matches("^[A-Za-z]{2}[0-9]{0,11}$");
                            if (!isOk) {
                                logger.error("{} - (查詢用戶出貨名單)追蹤號碼格式需為2碼英文+11碼數字（共13碼）", trackingNumber);
                                throw new BadRequestException("(查詢用戶出貨名單)追蹤號碼格式需為2碼英文+11碼數字（共13碼） - " + trackingNumber);
                            }
                        }
                        if (StringUtils.hasText(datePart)) {
                            logger.info("(查詢用戶出貨名單)日期範圍(YYYYMM) - {}", datePart);
                            boolean isNumber = datePart.matches("^\\d+$");
                            if (!isNumber) {
                                logger.error("{} - (查詢用戶出貨名單)日期範圍(YYYYMM)只能包含數字", datePart);
                                throw new BadRequestException("(查詢用戶出貨名單)日期範圍(YYYYMM)只能包含數字 - " + datePart);
                            }
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter
                                        .ofPattern("uuuuMM")
                                        .withResolverStyle(ResolverStyle.STRICT);
                                YearMonth.parse(datePart, formatter);
                            } catch (Exception e) {
                                logger.error("(查詢用戶出貨名單)日期範圍(YYYYMM)格式錯誤 - {}", datePart, e);
                                throw new BadRequestException("(查詢用戶出貨名單)日期範圍(YYYYMM)格式錯誤 - " + datePart, e);
                            }
                        }
                        List<Object> productsList = new ArrayList<>();
                        Shipments shipments = new Shipments(ordersIdBigDecimal);
                        shipments.setUsername(useruser);
                        shipments.setTracking_number(trackingNumber);
                        shipments.setDate_part(datePart);
                        shipments.setQuotationsStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()
                                )
                        );
                        shipments.setOrdersStatuss(
                                List.of(
                                        Backend.STATUS_ORDERS_CONFIRMED.getBackend()
                                )
                        );
                        shipments.setShipmentsStatuss(
                                List.of(
                                        Backend.STATUS_SHIPMENTS_PENDING.getBackend(),
                                        Backend.STATUS_SHIPMENTS_SHIPPED.getBackend(),
                                        Backend.STATUS_SHIPMENTS_DELIVERED.getBackend()
                                )
                        );
                        shipments.setPaymentsStatuss(
                                List.of(
                                        Backend.STATUS_PAYMENTS_UNPAID.getBackend(),
                                        Backend.STATUS_PAYMENTS_PARTIAL.getBackend(),
                                        Backend.STATUS_PAYMENTS_PAID.getBackend()
                                )
                        );
                        shipments.setPaymentsMethods(
                                List.of(
                                        Backend.METHOD_PAYMENTS_CASH.getBackend(),
                                        Backend.METHOD_PAYMENTS_CREDIT_CARD.getBackend(),
                                        Backend.METHOD_PAYMENTS_TRANSFER.getBackend()
                                )
                        );
                        List<Map<String, Object>> shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                        for (int i = 0; i < shipmentsData.size(); i++) {
                            String order_id = shipmentsData.get(i).get("order_id").toString();
                            String shipmentsUsername = shipmentsData.get(i).get("username").toString();
                            String tracking_number = shipmentsData.get(i).get("tracking_number").toString();
                            String status = shipmentsData.get(i).get("status").toString();
                            String paymentsStatus = shipmentsData.get(i).get("payments_status").toString();
                            String paymentsMethod = shipmentsData.get(i).get("payments_method").toString();
                            String paymentsAmount = shipmentsData.get(i).get("payments_amount").toString();
                            String ordersTotalPrice = shipmentsData.get(i).get("orders_total_price").toString();
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("----------第" + (i + 1) + "筆----------");
                            messageGroup.add("編號:" + order_id + ":用戶:" + shipmentsUsername);
                            messageGroup.add("追蹤號碼:" + tracking_number);
                            messageGroup.add("狀態:" + StatusKey.shipmentsStatusKey.get(status));
                            messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paymentsStatus));
                            messageGroup.add("付款方法:" + StatusKey.paymentsMethodKey.get(paymentsMethod));
                            messageGroup.add("已付金額:" + paymentsAmount);
                            messageGroup.add("需付款金額:" + ordersTotalPrice);
                            messageGroup.add("應付款金額:" +
                                    new BigDecimal(ordersTotalPrice).subtract(new BigDecimal(paymentsAmount)));
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "查詢用戶出貨名單",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (查詢用戶出貨名單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : shipmentsTrackingNumber 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 查詢用戶出貨名單，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> shipmentsShipped(ShipmentsTrackingNumberItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String trackingNumber = request.getTrackingNumber().trim().toUpperCase();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend shipmentsShipped 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (已出貨) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(已出貨)使用者錯誤");
                            throw new RuntimeException(username + " - (已出貨)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (已出貨)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(已出貨)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (已出貨) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (已出貨) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Shipments shipments = new Shipments();
                        shipments.setTracking_number(trackingNumber);
                        shipments.setQuotationsStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()
                                )
                        );
                        shipments.setOrdersStatuss(
                                List.of(
                                        Backend.STATUS_ORDERS_CONFIRMED.getBackend()
                                )
                        );
                        shipments.setShipmentsStatuss(
                                List.of(
                                        Backend.STATUS_SHIPMENTS_PENDING.getBackend()
                                )
                        );
                        shipments.setPaymentsStatuss(
                                List.of(
                                        Backend.STATUS_PAYMENTS_PARTIAL.getBackend(),
                                        Backend.STATUS_PAYMENTS_PAID.getBackend()
                                )
                        );
                        shipments.setPaymentsMethods(
                                List.of(
                                        Backend.METHOD_PAYMENTS_CASH.getBackend(),
                                        Backend.METHOD_PAYMENTS_CREDIT_CARD.getBackend(),
                                        Backend.METHOD_PAYMENTS_TRANSFER.getBackend()
                                )
                        );
                        List<Map<String, Object>> shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                        if (shipmentsData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String order_id = shipmentsData.getFirst().get("order_id").toString();
                            String shipmentsUsername = shipmentsData.getFirst().get("username").toString();
                            String tracking_number = shipmentsData.getFirst().get("tracking_number").toString();
                            String paymentsStatus = shipmentsData.getFirst().get("payments_status").toString();
                            String paymentsMethod = shipmentsData.getFirst().get("payments_method").toString();
                            String paymentsAmount = shipmentsData.getFirst().get("payments_amount").toString();
                            String ordersTotalPrice = shipmentsData.getFirst().get("orders_total_price").toString();
                            String shipped = Backend.STATUS_SHIPMENTS_SHIPPED.getBackend();
                            shipments.setStatus(shipped);
                            orderbackendMapper.updateShipments(shipments);
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("編號:" + order_id + ":用戶:" + shipmentsUsername);
                            messageGroup.add("追蹤號碼:" + tracking_number);
                            messageGroup.add("狀態:" + StatusKey.shipmentsStatusKey.get(shipped));
                            messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paymentsStatus));
                            messageGroup.add("付款方法:" + StatusKey.paymentsMethodKey.get(paymentsMethod));
                            messageGroup.add("已付金額:" + paymentsAmount);
                            messageGroup.add("需付款金額:" + ordersTotalPrice);
                            messageGroup.add("應付款金額:" +
                                    new BigDecimal(ordersTotalPrice).subtract(new BigDecimal(paymentsAmount)));
                            String partial = Backend.STATUS_PAYMENTS_PARTIAL.getBackend();
                            messageGroup.add(partial.equals(paymentsStatus)
                                    ? "----------未繳清金額----------"
                                    : "----------已繳清金額----------");
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "已出貨",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (已出貨)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : shipmentsShipped 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 已出貨，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> shipmentsDelivered(ShipmentsTrackingNumberItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String trackingNumber = request.getTrackingNumber().trim().toUpperCase();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend shipmentsDelivered 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (已送達) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(已送達)使用者錯誤");
                            throw new RuntimeException(username + " - (已送達)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (已送達)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(已送達)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (已送達) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);

                        Map<String, Object> userSelect;
                        String userOnly = RedisKey.redisUserKey.get("userOnly").replace("{1}", username);
                        String json = stringRedisTemplate.opsForValue().get(userOnly);
                        if (json != null) {
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {
                            });
                        } else {
                            userSelect = getUserData(userData);
                            String jsonMap = objectMapper.writeValueAsString(userSelect);
                            stringRedisTemplate.opsForValue().set(
                                    userOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                        }
                        if (userSelect == null) {
                            logger.error("{} : (已送達) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Shipments shipments = new Shipments();
                        shipments.setTracking_number(trackingNumber);
                        shipments.setQuotationsStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()
                                )
                        );
                        shipments.setOrdersStatuss(
                                List.of(
                                        Backend.STATUS_ORDERS_CONFIRMED.getBackend()
                                )
                        );
                        shipments.setShipmentsStatuss(
                                List.of(
                                        Backend.STATUS_SHIPMENTS_SHIPPED.getBackend()
                                )
                        );
                        shipments.setPaymentsStatuss(
                                List.of(
                                        Backend.STATUS_PAYMENTS_PAID.getBackend()
                                )
                        );
                        shipments.setPaymentsMethods(
                                List.of(
                                        Backend.METHOD_PAYMENTS_CASH.getBackend(),
                                        Backend.METHOD_PAYMENTS_CREDIT_CARD.getBackend(),
                                        Backend.METHOD_PAYMENTS_TRANSFER.getBackend()
                                )
                        );
                        List<Map<String, Object>> shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                        if (shipmentsData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String order_id = shipmentsData.getFirst().get("order_id").toString();
                            String shipmentsUsername = shipmentsData.getFirst().get("username").toString();
                            String tracking_number = shipmentsData.getFirst().get("tracking_number").toString();
                            String paymentsStatus = shipmentsData.getFirst().get("payments_status").toString();
                            String paymentsMethod = shipmentsData.getFirst().get("payments_method").toString();
                            String paymentsAmount = shipmentsData.getFirst().get("payments_amount").toString();
                            String ordersTotalPrice = shipmentsData.getFirst().get("orders_total_price").toString();
                            String delivered = Backend.STATUS_SHIPMENTS_DELIVERED.getBackend();
                            shipments.setStatus(delivered);
                            orderbackendMapper.updateShipments(shipments);
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("編號:" + order_id + ":用戶:" + shipmentsUsername);
                            messageGroup.add("追蹤號碼:" + tracking_number);
                            messageGroup.add("狀態:" + StatusKey.shipmentsStatusKey.get(delivered));
                            messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paymentsStatus));
                            messageGroup.add("付款方法:" + StatusKey.paymentsMethodKey.get(paymentsMethod));
                            messageGroup.add("已付金額:" + paymentsAmount);
                            messageGroup.add("需付款金額:" + ordersTotalPrice);
                            messageGroup.add("應付款金額:" +
                                    new BigDecimal(ordersTotalPrice).subtract(new BigDecimal(paymentsAmount)));
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "已送達",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (已送達)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : shipmentsDelivered 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 已送達，資源忙碌，請重試"
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

    @Override
    @Transactional
    @CheckRole(Permissions.ORDERBACKEND_ITEM_TOKEN)
    public ResponseEntity<?> shipmentsRollback(ShipmentsTrackingNumberItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String trackingNumber = request.getTrackingNumber().trim().toUpperCase();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend shipmentsRollback 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (恢復狀態) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(恢復狀態)使用者錯誤");
                            throw new RuntimeException(username + " - (恢復狀態)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (恢復狀態)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(恢復狀態)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (恢復狀態) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (恢復狀態) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Shipments shipments = new Shipments();
                        shipments.setTracking_number(trackingNumber);
                        shipments.setQuotationsStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()
                                )
                        );
                        shipments.setOrdersStatuss(
                                List.of(
                                        Backend.STATUS_ORDERS_CONFIRMED.getBackend()
                                )
                        );
                        shipments.setShipmentsStatuss(
                                List.of(
                                        Backend.STATUS_SHIPMENTS_SHIPPED.getBackend()
                                )
                        );
                        shipments.setPaymentsStatuss(
                                List.of(
                                        Backend.STATUS_PAYMENTS_PARTIAL.getBackend(),
                                        Backend.STATUS_PAYMENTS_PAID.getBackend()
                                )
                        );
                        shipments.setPaymentsMethods(
                                List.of(
                                        Backend.METHOD_PAYMENTS_CASH.getBackend(),
                                        Backend.METHOD_PAYMENTS_CREDIT_CARD.getBackend(),
                                        Backend.METHOD_PAYMENTS_TRANSFER.getBackend()
                                )
                        );
                        List<Map<String, Object>> shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                        if (shipmentsData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String order_id = shipmentsData.getFirst().get("order_id").toString();
                            String shipmentsUsername = shipmentsData.getFirst().get("username").toString();
                            String tracking_number = shipmentsData.getFirst().get("tracking_number").toString();
                            String paymentsStatus = shipmentsData.getFirst().get("payments_status").toString();
                            String paymentsMethod = shipmentsData.getFirst().get("payments_method").toString();
                            String paymentsAmount = shipmentsData.getFirst().get("payments_amount").toString();
                            String ordersTotalPrice = shipmentsData.getFirst().get("orders_total_price").toString();
                            String preparing = Backend.STATUS_SHIPMENTS_PENDING.getBackend();
                            shipments.setStatus(preparing);
                            orderbackendMapper.updateShipments(shipments);
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("編號:" + order_id + ":用戶:" + shipmentsUsername);
                            messageGroup.add("追蹤號碼:" + tracking_number);
                            messageGroup.add("狀態:" + StatusKey.shipmentsStatusKey.get(preparing));
                            messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paymentsStatus));
                            messageGroup.add("付款方法:" + StatusKey.paymentsMethodKey.get(paymentsMethod));
                            messageGroup.add("已付金額:" + paymentsAmount);
                            messageGroup.add("需付款金額:" + ordersTotalPrice);
                            messageGroup.add("應付款金額:" +
                                    new BigDecimal(ordersTotalPrice).subtract(new BigDecimal(paymentsAmount)));
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "恢復狀態",
                                productsList
                        );
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("1", messageList);
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (恢復狀態)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : shipmentsRollback 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 恢復狀態，資源忙碌，請重試"
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
