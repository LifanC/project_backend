package com.example.demo.Service;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.*;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Orderbackend.Payments;
import com.example.demo.Dto.Orderbackend.Shipments;
import com.example.demo.Dto.User.Orders;
import com.example.demo.Dto.Orderbackend.Quotations;
import com.example.demo.Dto.Orderbackend.UserDataSend;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderbackendMapper orderbackendMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public UserServiceImpl(
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
        logger.info("user/testLogin: User is working!");
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> dataMap = new TreeMap<>();
        dataMap.put("1", List.of("User is working!"));
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
    @CheckRole(Permissions.USER_ITEM_TOKEN)
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
    @CheckRole(Permissions.USER_ITEM_TOKEN)
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
    @CheckRole(Permissions.USER_ITEM_TOKEN)
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
    @CheckRole(Permissions.CAR_ITEM_QUERY)
    public ResponseEntity<?> productsCarSelect(QueryCarItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String product_id = request.getProduct_id().trim();
        boolean isNumber = false;
        if (StringUtils.hasText(product_id)) {
            isNumber = product_id.matches("^\\d+$");
        }
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User productsCarSelect 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢商品) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢商品)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢商品)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢商品)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢商品)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢商品) Token 已過期", usernameAccessJwt);
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
                            logger.error("{} : (查詢商品)查使用者帳號不存在", username);
                            throw new ResourceNotFoundException(username + " - (查詢商品)查使用者帳號不存在");
                        }
                        Product product = new Product();
                        if (isNumber) {
                            product.setProduct_id(new BigDecimal(product_id));
                        }
                        List<Map<String, Object>> productsCarSelect = getProduct(product);
                        logger.info("User 商品查詢成功");
                        List<Object> messageList = new ArrayList<>();
                        for (Map<String, Object> map : productsCarSelect) {
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("---------------------------------------");
                            messageGroup.add("商品編號 - " + map.get("product_id").toString());
                            messageGroup.add("商品名稱 - " + map.get("products_name").toString());
                            messageGroup.add("描述 - " + map.get("description").toString());
                            messageGroup.add("---------------------------------------");
                            messageList.add(messageGroup);
                        }
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
                        logger.error("{} : (查詢商品)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("User productsCarSelect 資源忙碌，請重試");
                List<Object> messageList = List.of(
                        "查詢，資源忙碌，請重試"
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

    private Map<String, Object> getUserDataDetail(UserdataDetails userdataDetails) {
        return userMapper.selectUserdataDetail(userdataDetails).get(userdataDetails.getUsername());
    }

    @Override
    @Transactional
    @CheckRole(Permissions.CAR_ITEM_CREATE)
    public ResponseEntity<?> createCarItem(CreateCarItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String product_id = request.getProduct_id().trim();
        boolean isNumber = product_id.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (新增購物車)商品編號只能包含數字", username);
            throw new BadRequestException(username + " - (新增購物車)商品編號只能包含數字");
        }
        final String product_quantity = request.getProduct_quantity().trim();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User createCarItem 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (新增購物車) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(新增購物車)使用者錯誤");
                            throw new RuntimeException(username + " - (新增購物車)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (新增購物車)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(新增購物車)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (新增購物車) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }

                        UserData userData = new UserData(username);
                        UserdataDetails userdataDetails = new UserdataDetails(username);

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
                            logger.error("{} : (新增購物車) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        try {
                            String msg;
                            List<Object> productsList = new ArrayList<>();
                            List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(product_id)));
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (productsSelect.isEmpty()) {
                                msg = username + " - " + product_id + " - 商品不存在";
                            } else {
                                if (userdataDetailsSelect == null) {
                                    productsList = List.of(
                                            "---------------------------------------",
                                            "商品編號 - " + productsSelect.getFirst().get("product_id").toString(),
                                            "商品名稱 - " + productsSelect.getFirst().get("products_name").toString(),
                                            "數量(增加) - " + new BigDecimal(product_quantity),
                                            "描述 - " + productsSelect.getFirst().get("description").toString(),
                                            "---------------------------------------"
                                    );

                                    userdataDetails.setOrder_item_str(product_id + ":" + product_quantity);
                                    userMapper.createUserdataDetail(userdataDetails);
                                } else {
                                    String orderItem = userdataDetailsSelect.get("order_item").toString();
                                    if (StringUtils.hasText(orderItem)) {
                                        Map<String, Integer> map = new TreeMap<>();
                                        for (String item : orderItem.split(",")) {
                                            String[] arr = item.split(":");
                                            map.put(arr[0], Integer.parseInt(arr[1]));
                                        }

                                        map.merge(product_id, Integer.parseInt(product_quantity), Integer::sum);
                                        List<String> list = map.entrySet().stream()
                                                .map(e -> e.getKey() + ":" + e.getValue())
                                                .toList();

                                        for (String item : list) {
                                            String[] arr = item.split(":");
                                            productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                            productsList.add("---------------------------------------");
                                            productsList.add("商品編號 - " + productsSelect.getFirst().get("product_id").toString());
                                            productsList.add("商品名稱 - " + productsSelect.getFirst().get("products_name").toString());
                                            productsList.add("數量(增加) - " + new BigDecimal(arr[1]));
                                            productsList.add("描述 - " + productsSelect.getFirst().get("description").toString());
                                            productsList.add("---------------------------------------");
                                        }

                                        String result = list.stream()
                                                .map(String::valueOf)
                                                .collect(Collectors.joining(","));
                                        userdataDetails.setOrder_item_str(result);
                                    } else {
                                        userdataDetails.setOrder_item_str(product_id + ":" + product_quantity);
                                    }
                                    userMapper.updateUserdataDetail(userdataDetails);
                                }
                                msg = username + " - 商品編號(" + product_id + ") - 新增商品成功";
                            }

                            List<Object> messageList = List.of(
                                    "帳號 - " + username,
                                    "權限 - " + userSelect.get("permissions").toString(),
                                    msg,
                                    productsList,
                                    "新增日期" + ConvertFormat.time("")
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
                        } catch (DataIntegrityViolationException e) {
                            logger.warn("新增購物車資料不合法，username={}", usernameAccessJwt);
                            throw new IsViolationException(username + " - 新增購物車資料不合法", e);
                        } catch (DataAccessException e) {
                            logger.error("新增資料庫錯誤，username={}", usernameAccessJwt);
                            throw new DBException(username + " - 系統錯誤，請稍後再試", e);
                        }
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (新增購物車)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : createCarItem 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 新增購物車，資源忙碌，請重試"
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
    @CheckRole(Permissions.CAR_ITEM_QUERY)
    public ResponseEntity<?> queryCarItem(QueryCarItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User queryCarItem 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢購物車) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢購物車)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢購物車)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢購物車)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢購物車)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢購物車) Token 已過期", usernameAccessJwt);
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
                            logger.error("{} : (查詢購物車) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        String permissions = userSelect.get("permissions").toString();
                        List<Object> messageList = new ArrayList<>();
                        messageList.add("帳號 - " + username);
                        messageList.add("權限 - " + permissions);
                        UserdataDetails userdataDetails = new UserdataDetails(username);
                        Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                        if (userdataDetailsSelect == null) {
                            logger.error("{} : (查詢購物車) 訂單不存在", username);
                            throw new ResourceNotFoundException(username + " - 訂單不存在");
                        }
                        String orderItem = userdataDetailsSelect.get("order_item").toString();
                        logger.info("(查詢購物車){}", orderItem);
                        String[] list = orderItem.split(",");
                        int listLength = 0;
                        if (StringUtils.hasText(orderItem)) {
                            listLength = list.length;
                            messageList.add(username + " - 查詢購物車成功");
                        } else {
                            messageList.add(username + " - 新增商品至購物車");
                        }
                        List<Object> productsList = new ArrayList<>();
                        for (int i = 0; i < listLength; i++) {
                            final int num = i + 1;
                            String[] arr = list[i].split(":");
                            List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                            productsList.add("---------------------------------------");
                            productsList.add("第" + num + "筆");
                            productsList.add("商品編號 - " + productsSelect.getFirst().get("product_id").toString());
                            productsList.add("商品名稱 - " + productsSelect.getFirst().get("products_name").toString());
                            productsList.add("數量 - " + new BigDecimal(arr[1]));
                            productsList.add("---------------------------------------");
                        }
                        messageList.add(productsList);
                        messageList.add("新增訂單日期 - " + ConvertFormat.time(userdataDetailsSelect.get("created_date").toString()));
                        messageList.add("更改訂單日期 - " + ConvertFormat.time(userdataDetailsSelect.get("updated_date").toString()));
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
                        logger.error("{} : (查詢購物車)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : queryCarItem 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 查詢購物車，資源忙碌，請重試"
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
    @CheckRole(Permissions.CAR_ITEM_UPDATE)
    public ResponseEntity<?> updateCarItem(UpdateCarItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String product_id = request.getProduct_id().trim();
        boolean isNumber = product_id.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (更改購物車)商品編號只能包含數字", username);
            throw new BadRequestException(username + " - (更改購物車)商品編號只能包含數字");
        }
        final String product_quantity = request.getProduct_quantity().trim();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User updateCarItem 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (更改購物車) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(更改購物車)使用者錯誤");
                            throw new RuntimeException(username + " - (更改購物車)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (更改購物車)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(更改購物車)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (更改購物車) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        UserdataDetails userdataDetails = new UserdataDetails(username);

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
                            logger.error("{} : (更改購物車) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        try {
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (userdataDetailsSelect == null) {
                                logger.error("{} : (更改購物車) 訂單不存在", username);
                                throw new ResourceNotFoundException(username + " - 訂單不存在");
                            }

                            String orderItem = userdataDetailsSelect.get("order_item").toString();
                            logger.info("(更改購物車){}", orderItem);
                            List<Object> productsList = new ArrayList<>();
                            String msg;
                            if (StringUtils.hasText(orderItem)) {
                                Map<String, Integer> map = new TreeMap<>();
                                for (String item : orderItem.split(",")) {
                                    String[] arr = item.split(":");
                                    map.put(arr[0], Integer.parseInt(arr[1]));
                                }
                                int qty = Integer.parseInt(product_quantity);
                                map.put(product_id, Math.max(1, map.getOrDefault(product_id, 0) - qty));
                                List<String> list = map.entrySet().stream()
                                        .map(e -> e.getKey() + ":" + e.getValue())
                                        .toList();

                                for (String item : list) {
                                    String[] arr = item.split(":");
                                    List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                    productsList.add("---------------------------------------");
                                    productsList.add("商品編號 - " + productsSelect.getFirst().get("product_id").toString());
                                    productsList.add("商品名稱 - " + productsSelect.getFirst().get("products_name").toString());
                                    productsList.add("數量(減少) - " + new BigDecimal(arr[1]));
                                    productsList.add("描述 - " + productsSelect.getFirst().get("description").toString());
                                    productsList.add("---------------------------------------");
                                }

                                String result = list.stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","));
                                userdataDetails.setOrder_item_str(result);
                                userMapper.updateUserdataDetail(userdataDetails);
                                msg = username + " - 商品編號(" + product_id + ") - 更改商品成功";
                            } else {
                                msg = username + " - 新增商品至購物車";
                            }
                            List<Object> messageList = List.of(
                                    "帳號 - " + username,
                                    "權限 - " + userSelect.get("permissions").toString(),
                                    msg,
                                    productsList,
                                    "更改日期" + ConvertFormat.time("")
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
                        } catch (DataIntegrityViolationException e) {
                            logger.warn("更改購物車資料不合法，username={}", usernameAccessJwt);
                            throw new IsViolationException(username + " - 更改購物車資料不合法", e);
                        } catch (DataAccessException e) {
                            logger.error("更改購物車資料庫錯誤，username={}", usernameAccessJwt);
                            throw new DBException(username + " - 系統錯誤，請稍後再試", e);
                        }
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (更改購物車)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : updateCarItem 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 更改購物車，資源忙碌，請重試"
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
    @CheckRole(Permissions.CAR_ITEM_DELETE)
    public ResponseEntity<?> deleteCarItem(DeleteCarItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String product_id = request.getProduct_id().trim();
        boolean isNumber = product_id.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (刪除購物車)商品編號只能包含數字", username);
            throw new BadRequestException(username + " - (刪除購物車)商品編號只能包含數字");
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User deleteCarItem 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (刪除購物車) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(刪除購物車)使用者錯誤");
                            throw new RuntimeException(username + " - (刪除購物車)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (刪除購物車)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(刪除購物車)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (刪除購物車) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }

                        UserData userData = new UserData(username);
                        UserdataDetails userdataDetails = new UserdataDetails(username);

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
                            logger.error("{} : (刪除購物車) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        try {
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (userdataDetailsSelect == null) {
                                logger.error("{} : (刪除購物車) 訂單不存在", username);
                                throw new ResourceNotFoundException(username + " - 訂單不存在");
                            }
                            String orderItem = userdataDetailsSelect.get("order_item").toString();
                            logger.info("(刪除購物車){}", orderItem);
                            List<String> hasNotSameList = new ArrayList<>();
                            List<String> hasNotSameListUpdateDb = new ArrayList<>();
                            List<String> tempList = new ArrayList<>();
                            String[] orderItems = orderItem.split(",");
                            for (String item : orderItems) {
                                String[] arr = item.split(":");
                                if (!product_id.equals(arr[0])) {
                                    hasNotSameList.add(arr[0]);
                                    hasNotSameListUpdateDb.add(item);
                                } else {
                                    tempList.add(arr[0]);
                                }
                            }
                            List<Object> productsList = new ArrayList<>();
                            if (!tempList.isEmpty()) {
                                List<Map<String, Object>> productsSelect;
                                for (String temp : tempList) {
                                    productsSelect = getProduct(new Product(new BigDecimal(temp)));
                                    String delProduct_id = productsSelect.getFirst().get("product_id").toString();
                                    String delProduct_name = productsSelect.getFirst().get("products_name").toString();
                                    productsList.add("刪除 - 商品編號(" + delProduct_id + ") - " + delProduct_name);
                                }
                                productsList.add("---------------------------------------");
                                for (String hasNotSame : hasNotSameList) {
                                    productsSelect = getProduct(new Product(new BigDecimal(hasNotSame)));
                                    String hasNotProduct_id = productsSelect.getFirst().get("product_id").toString();
                                    String hasNotProduct_name = productsSelect.getFirst().get("products_name").toString();
                                    productsList.add("商品編號(" + hasNotProduct_id + ") - " + hasNotProduct_name);
                                }
                                String result = hasNotSameListUpdateDb.stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","));
                                userdataDetails.setOrder_item_str(result);
                                userMapper.updateUserdataDetail(userdataDetails);
                            } else {
                                productsList = List.of(username + " - 商品編號(" + product_id + ") - 無此商品");
                            }
                            List<Object> messageList = List.of(
                                    "帳號 - " + username,
                                    "權限 - " + userSelect.get("permissions").toString(),
                                    productsList,
                                    "刪除日期" + ConvertFormat.time("")
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
                        } catch (DataAccessException e) {
                            logger.error("刪除資料庫錯誤，username={}", usernameAccessJwt);
                            throw new DBException(username + " - 系統錯誤，請稍後再試", e);
                        }
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (刪除購物車)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : deleteCarItem 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 購物車刪除，資源忙碌，請重試"
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
    @CheckRole(Permissions.USER_ITEM_CONFIRM)
    public ResponseEntity<?> confirmItem(ConfirmItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User confirmItem 拿鎖");
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
                        List<Object> messageList = new ArrayList<>();
                        messageList.add("帳號 - " + username);
                        messageList.add("權限 - " + userSelect.get("permissions").toString());
                        UserdataDetails userdataDetails = new UserdataDetails(username);
                        Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                        if (userdataDetailsSelect == null) {
                            logger.error("{} : (確認訂單) 訂單不存在", username);
                            throw new ResourceNotFoundException(username + " - 訂單不存在");
                        }
                        String orderItem = userdataDetailsSelect.get("order_item").toString();
                        logger.info("(確認訂單){}", orderItem);
                        if (StringUtils.hasText(orderItem)) {
                            userMapper.updateUserdataDetailIsActive(username);
                            messageList.add(username + " - 商品下單成功");
                        } else {
                            messageList.add(username + " - 新增商品至購物車");
                        }
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
                logger.error("{} : confirmItem 資源忙碌，請重試", username);
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
    @CheckRole(Permissions.USER_ITEM_QUOTATIONS)
    public ResponseEntity<?> quotationsProductId(QuotationsProductIdRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User quotationsProductId 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (報價單編號) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(報價單編號)使用者錯誤");
                            throw new RuntimeException(username + " - (報價單編號)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (報價單編號)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(報價單編號)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (報價單編號) Token 已過期", usernameAccessJwt);
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
                            logger.error("{} : (報價單編號) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        QuotationsProduct quotationsProduct = new QuotationsProduct();
                        quotationsProduct.setUsername(username);
                        quotationsProduct.setStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_SENT.getBackend(),
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend(),
                                        Backend.STATUS_QUOTATIONS_REJECTED.getBackend()
                                )
                        );
                        List<Map<String, Object>> quotationsIdData = userMapper.userdataDetailsDataId(quotationsProduct);
                        if (quotationsIdData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            List<Object> messageGroup = new ArrayList<>();
                            for (int i = 0; i < quotationsIdData.size(); i++) {
                                String quotation_id = quotationsIdData.get(i).get("quotation_id").toString();
                                String status = quotationsIdData.get(i).get("status").toString();
                                messageGroup.add("第" + (i + 1) + "筆 - " +
                                        "編號:" + quotation_id + ":" +
                                        "狀態:" + StatusKey.quotationsStatusKey.get(status));
                            }
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "報價單編號",
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
                        logger.error("{} : (報價單編號)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : quotationsProductId 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 報價單，資源忙碌，請重試"
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
    @CheckRole(Permissions.USER_ITEM_QUOTATIONS)
    public ResponseEntity<?> quotationsProduct(QuotationsProductRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String quotation_id = request.getQuotation_id().trim();
        boolean isNumber = quotation_id.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (報價單)商品編號只能包含數字", username);
            throw new BadRequestException(username + " - (報價單)商品編號只能包含數字");
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User quotationsProduct 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (報價單) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(報價單)使用者錯誤");
                            throw new RuntimeException(username + " - (報價單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (報價單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(報價單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (報價單) Token 已過期", usernameAccessJwt);
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
                            logger.error("{} : (報價單) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        QuotationsProduct quotationsProduct = new QuotationsProduct(new BigDecimal(quotation_id));
                        quotationsProduct.setUsername(username);
                        quotationsProduct.setStatuss(
                                List.of(
                                        Backend.STATUS_QUOTATIONS_SENT.getBackend(),
                                        Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend(),
                                        Backend.STATUS_QUOTATIONS_REJECTED.getBackend()
                                )
                        );
                        List<Map<String, Object>> quotationsData = userMapper.userdataDetailsData(quotationsProduct);
                        if (quotationsData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("報價單編號: " + quotation_id);
                            for (int i = 0; i < quotationsData.size(); i++) {
                                String status = quotationsData.get(i).get("status").toString();
                                BigDecimal quantity = new BigDecimal(quotationsData.get(i).get("quantity").toString());
                                BigDecimal price = new BigDecimal(quotationsData.get(i).get("price").toString());
                                BigDecimal sumPrice = new BigDecimal(quotationsData.get(i).get("sum_price").toString());
                                String productsName = quotationsData.get(i).get("products_name").toString();
                                String description = quotationsData.get(i).get("description").toString();
                                messageGroup.add("┌-------第" + (i + 1) + "筆-------┐");
                                messageGroup.add("|數量:" + quantity);
                                messageGroup.add("|價格:" + price);
                                messageGroup.add("|合計:" + sumPrice);
                                messageGroup.add("|名稱:" + productsName);
                                messageGroup.add("|描述:" + description);
                                messageGroup.add("|狀態:" + StatusKey.quotationsStatusKey.get(status));
                                messageGroup.add("└------------------┘");
                            }
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "報價單",
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
                        logger.error("{} : (報價單)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : quotationsProduct 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 報價單，資源忙碌，請重試"
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
    @CheckRole(Permissions.USER_ITEM_QUOTATIONS)
    public ResponseEntity<?> userAccepted(QuotationsProductRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String quotation_id = request.getQuotation_id().trim();
        boolean isNumber = quotation_id.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (接受)商品編號只能包含數字", username);
            throw new BadRequestException(username + " - (接受)商品編號只能包含數字");
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User userAccepted 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (接受) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(接受)使用者錯誤");
                            throw new RuntimeException(username + " - (接受)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (接受)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(接受)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (接受) Token 已過期", usernameAccessJwt);
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
                            logger.error("{} : (接受) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Quotations quotations = new Quotations(new BigDecimal(quotation_id));
                        quotations.setUsername(username);
                        Map<String, Object> quotationsDetails = userMapper.selectQuotations(quotations).get(username);
                        if (quotationsDetails == null) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String quotationDetails_id = quotationsDetails.get("quotation_id").toString();
                            String status = quotationsDetails.get("status").toString();
                            String send = Backend.STATUS_QUOTATIONS_SENT.getBackend();
                            if (status.equals(send)) {
                                UserDataSend userDataDetails = new UserDataSend(username);
                                String accepted = Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend();
                                userDataDetails.setStatus(accepted);
                                userDataDetails.setQuotation_id(new BigDecimal(quotationDetails_id));
                                userMapper.updateQuotations(userDataDetails);
                                Integer ordersItemsMax = userMapper.selectOrdersItemsMax();
                                BigDecimal decimal = new BigDecimal(String.valueOf(ordersItemsMax + 1));
                                Orders order = new Orders(decimal, new BigDecimal(quotationDetails_id));
                                String pending = Backend.STATUS_ORDERS_PENDING.getBackend();
                                order.setUsername(username);
                                order.setStatus(pending);
                                BigDecimal total_price = new BigDecimal(quotationsDetails.get("total_price").toString());
                                order.setTotal_price(total_price);
                                userMapper.createOrders(order);
                                List<Map<String, Object>> getOrders = userMapper.getOrdersQuotations(quotations);
                                List<Object> messageOrdersGroup = new ArrayList<>();
                                for (int i = 0; i < getOrders.size(); i++) {
                                    Map<String, Object> getOrder = getOrders.get(i);
                                    BigDecimal quantity = new BigDecimal(getOrder.get("quantity").toString());
                                    BigDecimal price = new BigDecimal(getOrder.get("price").toString());
                                    BigDecimal sumPrice = new BigDecimal(getOrder.get("sum_price").toString());
                                    String productsName = getOrder.get("products_name").toString();
                                    String description = getOrder.get("description").toString();
                                    messageOrdersGroup.add("┌----訂單-第" + (i + 1) + "筆-----┐");
                                    messageOrdersGroup.add("|數量:" + quantity);
                                    messageOrdersGroup.add("|價格:" + price);
                                    messageOrdersGroup.add("|合計:" + sumPrice);
                                    messageOrdersGroup.add("|名稱:" + productsName);
                                    messageOrdersGroup.add("|描述:" + description);
                                    messageOrdersGroup.add("|狀態:" + StatusKey.ordersStatusKey.get(pending));
                                    messageOrdersGroup.add("└------------------┘");
                                }
                                List<Object> messageGroup = List.of(
                                        "┌---------商品---------┐",
                                        "|報價單編號: " + quotation_id,
                                        "|總價格:" + total_price,
                                        "|狀態:" + StatusKey.quotationsStatusKey.get(accepted),
                                        messageOrdersGroup,
                                        "└---------商品---------┘"
                                );
                                productsList.add(messageGroup);
                            } else {
                                List<Object> messageGroup = List.of("空");
                                productsList = List.of(messageGroup);
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "接受",
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
                        logger.error("{} : (接受)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : userAccepted 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 接受，資源忙碌，請重試"
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
    @CheckRole(Permissions.USER_ITEM_QUOTATIONS)
    public ResponseEntity<?> userRejected(QuotationsProductRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String quotation_id = request.getQuotation_id().trim();
        boolean isNumber = quotation_id.matches("^\\d+$");
        if (!isNumber) {
            logger.error("{} - (拒絕)商品編號只能包含數字", username);
            throw new BadRequestException(username + " - (拒絕)商品編號只能包含數字");
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User userRejected 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (拒絕) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(拒絕)使用者錯誤");
                            throw new RuntimeException(username + " - (拒絕)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (拒絕)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(拒絕)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (拒絕) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (拒絕) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Quotations quotations = new Quotations(new BigDecimal(quotation_id));
                        quotations.setUsername(username);
                        Map<String, Object> quotationsDetails = userMapper.selectQuotations(quotations).get(username);
                        if (quotationsDetails == null) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String status = quotationsDetails.get("status").toString();
                            String send = Backend.STATUS_QUOTATIONS_SENT.getBackend();
                            if (status.equals(send)) {
                                UserDataSend userDataDetails = new UserDataSend(username);
                                String rejected = Backend.STATUS_QUOTATIONS_REJECTED.getBackend();
                                userDataDetails.setStatus(rejected);
                                userDataDetails.setQuotation_id(new BigDecimal(quotation_id));
                                userMapper.updateQuotations(userDataDetails);
                                BigDecimal total_price = new BigDecimal(quotationsDetails.get("total_price").toString());
                                List<Object> messageGroup = List.of(
                                        "┌---------商品---------┐",
                                        "|報價單編號: " + quotation_id,
                                        "|總價格:" + total_price,
                                        "|狀態:" + StatusKey.quotationsStatusKey.get(rejected),
                                        "└---------商品---------┘"
                                );
                                productsList.add(messageGroup);
                            } else {
                                List<Object> messageGroup = List.of("空");
                                productsList = List.of(messageGroup);
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "拒絕",
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
                        logger.error("{} : (拒絕)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : userRejected 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 拒絕，資源忙碌，請重試"
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
    @CheckRole(Permissions.USER_ITEM_SHIPMENTS)
    public ResponseEntity<?> userShipments(ShipmentsRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String orderId = request.getOrderId();
        final String trackingNumber = request.getTrackingNumber().trim().toUpperCase();
        final String datePart = request.getDatePart().trim();
        final String shipmentsStatus = request.getShipmentsStatus().trim();
        final String paymentsStatus = request.getPaymentsStatus().trim();
        final String paymentsMethod = request.getPaymentsMethod().trim();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User userShipments 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢出貨資訊) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢出貨資訊)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢出貨資訊)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢出貨資訊)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢出貨資訊)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢出貨資訊) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (查詢出貨資訊) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        BigDecimal ordersIdBigDecimal = null;
                        if (StringUtils.hasText(orderId)) {
                            logger.info("(查詢出貨資訊)出貨單編號 - {}", orderId);
                            boolean isNumber = orderId.matches("^\\d+$");
                            if (!isNumber) {
                                logger.error("{} - (查詢出貨資訊)出貨編號只能包含數字", orderId);
                                throw new BadRequestException(orderId + " - (查詢出貨資訊)出貨編號只能包含數字");
                            }
                            ordersIdBigDecimal = new BigDecimal(orderId);
                        }
                        if (StringUtils.hasText(trackingNumber)) {
                            logger.info("(查詢出貨資訊)追蹤號碼(13碼) - {}", trackingNumber);
                            boolean isOk = trackingNumber.matches("^[A-Za-z]{2}[0-9]{0,11}$");
                            if (!isOk) {
                                logger.error("{} - (查詢出貨資訊)追蹤號碼格式需為2碼英文+11碼數字（共13碼）", trackingNumber);
                                throw new BadRequestException("(查詢出貨資訊)追蹤號碼格式需為2碼英文+11碼數字（共13碼） - " + trackingNumber);
                            }
                        }
                        if (StringUtils.hasText(datePart)) {
                            logger.info("(查詢出貨資訊)日期範圍(YYYYMM) - {}", datePart);
                            boolean isNumber = datePart.matches("^\\d+$");
                            if (!isNumber) {
                                logger.error("{} - (查詢出貨資訊)日期範圍(YYYYMM)只能包含數字", datePart);
                                throw new BadRequestException("(查詢出貨資訊)日期範圍(YYYYMM)只能包含數字 - " + datePart);
                            }
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter
                                        .ofPattern("uuuuMM")
                                        .withResolverStyle(ResolverStyle.STRICT);
                                YearMonth.parse(datePart, formatter);
                            } catch (Exception e) {
                                logger.error("(查詢出貨資訊)日期範圍(YYYYMM)格式錯誤 - {}", datePart, e);
                                throw new BadRequestException("(查詢出貨資訊)日期範圍(YYYYMM)格式錯誤 - " + datePart, e);
                            }
                        }
                        List<Object> productsList = new ArrayList<>();
                        Shipments shipments = new Shipments(ordersIdBigDecimal);
                        shipments.setUsername(username);
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
                        List<String> shipmentsStatusList = new ArrayList<>();
                        List<String> paymentsStatusList = new ArrayList<>();
                        List<String> paymentsMethodList = new ArrayList<>();
                        List<String> includeOther = new ArrayList<>();
                        includeOther.add(0, "");
                        includeOther.add(1, "");
                        includeOther.add(2, "");
                        if (StringUtils.hasText(shipmentsStatus)) {
                            try {
                                String name = Backend.fromBackend(shipmentsStatus).name();
                                shipmentsStatusList.add(Backend.valueOf(name).getBackend());
                            } catch (Exception e) {
                                includeOther.set(0, "other");
                            }
                        } else {
                            throw new BadRequestException("(查詢出貨資訊)出貨狀態不存在 - " + shipmentsStatus);
                        }
                        if (StringUtils.hasText(paymentsStatus)) {
                            try {
                                String name = Backend.fromBackend(paymentsStatus).name();
                                paymentsStatusList.add(Backend.valueOf(name).getBackend());
                            } catch (Exception e) {
                                includeOther.set(1, "other");
                            }
                        } else {
                            throw new BadRequestException("(查詢出貨資訊)付款狀態不存在 - " + shipmentsStatus);
                        }
                        if (StringUtils.hasText(paymentsMethod)) {
                            try {
                                String name = Backend.fromBackend(paymentsMethod).name();
                                paymentsMethodList.add(Backend.valueOf(name).getBackend());
                            } catch (Exception e) {
                                includeOther.set(2, "other");
                            }
                        } else {
                            throw new BadRequestException("(查詢出貨資訊)付款方法不存在 - " + shipmentsStatus);
                        }
                        boolean otherBoolean = false;
                        List<Map<String, Object>> shipmentsData = new ArrayList<>();
                        if (includeOther.stream().allMatch(other -> other.equals("other"))) {
                            otherBoolean = true;
                        } else {
                            shipments.setShipmentsStatuss(shipmentsStatusList);
                            shipments.setPaymentsStatuss(paymentsStatusList);
                            shipments.setPaymentsMethods(paymentsMethodList);
                            shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                        }
                        if (otherBoolean || shipmentsData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            for (int i = 0; i < shipmentsData.size(); i++) {
                                String order_id = shipmentsData.get(i).get("order_id").toString();
                                String shipmentsUsername = shipmentsData.get(i).get("username").toString();
                                String date = shipmentsData.get(i).get("date_part").toString();
                                String tracking_number = shipmentsData.get(i).get("tracking_number").toString();
                                String userStatus = shipmentsData.get(i).get("status").toString();
                                String userPaymentsStatus = shipmentsData.get(i).get("payments_status").toString();
                                String userPaymentsMethod = shipmentsData.get(i).get("payments_method").toString();
                                String userPaymentsAmount = shipmentsData.get(i).get("payments_amount").toString();
                                String userOrdersTotalPrice = shipmentsData.get(i).get("orders_total_price").toString();
                                List<Object> messageGroup = new ArrayList<>();
                                messageGroup.add("----------第" + (i + 1) + "筆----------");
                                messageGroup.add("訂單編號:" + order_id);
                                messageGroup.add("用戶:" + shipmentsUsername);
                                messageGroup.add("出貨日期:" + date);
                                messageGroup.add("追蹤號碼:" + tracking_number);
                                messageGroup.add("出貨狀態:" + StatusKey.shipmentsStatusKey.get(userStatus));
                                messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(userPaymentsStatus));
                                messageGroup.add("付款方法:" + StatusKey.paymentsMethodKey.get(userPaymentsMethod));
                                messageGroup.add("已付金額:" + userPaymentsAmount);
                                messageGroup.add("需付款金額:" + userOrdersTotalPrice);
                                messageGroup.add("應付款金額:" +
                                        new BigDecimal(userOrdersTotalPrice).subtract(new BigDecimal(userPaymentsAmount)));
                                productsList.add(messageGroup);
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "查詢出貨資訊",
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
                        logger.error("{} : (查詢出貨資訊)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : userShipments 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 查詢出貨資訊，資源忙碌，請重試"
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
    @CheckRole(Permissions.USER_ITEM_SHIPMENTS)
    public ResponseEntity<?> userPayments(PaymentsRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String trackingNumber = request.getTrackingNumber().trim().toUpperCase();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User userPayments 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (查詢付款資訊) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢付款資訊)使用者錯誤");
                            throw new RuntimeException(username + " - (查詢付款資訊)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢付款資訊)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢付款資訊)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢付款資訊) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (查詢付款資訊) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        if (StringUtils.hasText(trackingNumber)) {
                            logger.info("(查詢付款資訊)追蹤號碼(13碼) - {}", trackingNumber);
                            boolean isOk = trackingNumber.matches("^[A-Za-z]{2}[0-9]{0,11}$");
                            if (!isOk) {
                                logger.error("{} - (查詢付款資訊)追蹤號碼格式需為2碼英文+11碼數字（共13碼）", trackingNumber);
                                throw new BadRequestException("(查詢付款資訊)追蹤號碼格式需為2碼英文+11碼數字（共13碼） - " + trackingNumber);
                            }
                        }
                        List<Object> productsList = new ArrayList<>();
                        Shipments shipments = new Shipments();
                        shipments.setUsername(username);
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
                            String date = shipmentsData.get(i).get("date_part").toString();
                            String tracking_number = shipmentsData.get(i).get("tracking_number").toString();
                            String userStatus = shipmentsData.get(i).get("status").toString();
                            String userPaymentsStatus = shipmentsData.get(i).get("payments_status").toString();
                            String userPaymentsMethod = shipmentsData.get(i).get("payments_method").toString();
                            String userPaymentsAmount = shipmentsData.get(i).get("payments_amount").toString();
                            String userOrdersTotalPrice = shipmentsData.get(i).get("orders_total_price").toString();
                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("----------第" + (i + 1) + "筆----------");
                            messageGroup.add("訂單編號:" + order_id);
                            messageGroup.add("用戶:" + shipmentsUsername);
                            messageGroup.add("出貨日期:" + date);
                            messageGroup.add("追蹤號碼:" + tracking_number);
                            messageGroup.add("出貨狀態:" + StatusKey.shipmentsStatusKey.get(userStatus));
                            messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(userPaymentsStatus));
                            messageGroup.add("付款方法:" + StatusKey.paymentsMethodKey.get(userPaymentsMethod));
                            messageGroup.add("已付金額:" + userPaymentsAmount);
                            messageGroup.add("需付款金額:" + userOrdersTotalPrice);
                            messageGroup.add("應付款金額:" +
                                    new BigDecimal(userOrdersTotalPrice).subtract(new BigDecimal(userPaymentsAmount)));
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "查詢付款資訊",
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
                        logger.error("{} : (查詢付款資訊)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : userPayments 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 查詢付款資訊，資源忙碌，請重試"
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
    @CheckRole(Permissions.USER_ITEM_SHIPMENTS)
    public ResponseEntity<?> userPayMoney(PayMoneyRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String trackingNumber = request.getTrackingNumber().trim().toUpperCase();
        final String amount = request.getAmount().trim();
        final String paymentsMethod = request.getPaymentsMethod().trim();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User userPayMoney 拿鎖");
                try {
                    try {
                        String refreshRedisKey = RedisKey.redisKey.get("refresh").replace("{1}", username);
                        Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                        if (Boolean.FALSE.equals(exists)) {
                            logger.error("{} : (付款) Token 不存在或已過期", username);
                            throw new BadRequestException(username + " - Token 不存在或已過期");
                        }
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(付款)使用者錯誤");
                            throw new RuntimeException(username + " - (付款)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (付款)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(付款)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);

                        String accessRedisKey = RedisKey.redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (付款) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (付款) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        Shipments shipments = new Shipments();
                        shipments.setUsername(username);
                        shipments.setTracking_number(trackingNumber);
                        shipments.setQuotationsStatuss(List.of(Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()));
                        shipments.setOrdersStatuss(List.of(Backend.STATUS_ORDERS_CONFIRMED.getBackend()));
                        List<String> shipmentsStatusList = List.of(
                                Backend.STATUS_SHIPMENTS_PENDING.getBackend()
                        );
                        List<String> paymentsStatusList = List.of(
                                Backend.STATUS_PAYMENTS_UNPAID.getBackend(),
                                Backend.STATUS_PAYMENTS_PARTIAL.getBackend()
                        );
                        List<String> paymentsMethodList = List.of(
                                Backend.METHOD_PAYMENTS_CASH.getBackend(),
                                Backend.METHOD_PAYMENTS_CREDIT_CARD.getBackend(),
                                Backend.METHOD_PAYMENTS_TRANSFER.getBackend()
                        );
                        List<String> includeOther = new ArrayList<>();
                        boolean otherBoolean = false;
                        try {
                            Backend.fromBackend(paymentsMethod);
                        } catch (Exception e) {
                            includeOther.add("other");
                        }
                        List<Map<String, Object>> shipmentsData = new ArrayList<>();
                        if (includeOther.stream().anyMatch(other -> other.equals("other"))) {
                            otherBoolean = true;
                        } else {
                            shipments.setShipmentsStatuss(shipmentsStatusList);
                            shipments.setPaymentsStatuss(paymentsStatusList);
                            shipments.setPaymentsMethods(paymentsMethodList);
                            shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                        }
                        if (otherBoolean || shipmentsData.isEmpty()) {
                            List<Object> messageGroup = List.of("空");
                            productsList = List.of(messageGroup);
                        } else {
                            String order_id = shipmentsData.getFirst().get("order_id").toString();
                            String shipmentsUsername = shipmentsData.getFirst().get("username").toString();
                            String shipmentsDate = shipmentsData.getFirst().get("date_part").toString();
                            String shipmentsTrackingNumber = shipmentsData.getFirst().get("tracking_number").toString();
                            String userStatus = shipmentsData.getFirst().get("status").toString();
                            String userPaymentsStatus = shipmentsData.getFirst().get("payments_status").toString();
                            String userPaymentsMethod = shipmentsData.getFirst().get("payments_method").toString();
                            String userPaymentsAmount = shipmentsData.getFirst().get("payments_amount").toString();
                            String userOrdersTotalPrice = shipmentsData.getFirst().get("orders_total_price").toString();

                            List<Object> messageGroup = new ArrayList<>();
                            messageGroup.add("訂單編號:" + order_id);
                            messageGroup.add("用戶:" + shipmentsUsername);
                            messageGroup.add("出貨日期:" + shipmentsDate);
                            messageGroup.add("追蹤號碼:" + shipmentsTrackingNumber);
                            messageGroup.add("出貨狀態:" + StatusKey.shipmentsStatusKey.get(userStatus));
                            Payments payments = new Payments(new BigDecimal(order_id));
                            BigDecimal A = new BigDecimal(userOrdersTotalPrice);
                            BigDecimal B = new BigDecimal(userPaymentsAmount);
                            BigDecimal sumAmount = B.add(new BigDecimal(amount));
                            BigDecimal C = A.subtract(sumAmount);
                            boolean doNotChange = false;
                            if (BigDecimal.ZERO.compareTo(new BigDecimal(userPaymentsAmount)) != 0) {
                                if (!userPaymentsMethod.equals(paymentsMethod)) {
                                    doNotChange = true;
                                }
                            }
                            if (!doNotChange) {
                                if (C.compareTo(BigDecimal.ZERO) < 0) {
                                    messageGroup.add("----------金額多繳:" + C.abs() + ":----------");
                                    messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(userPaymentsStatus));
                                } else if (C.compareTo(BigDecimal.ZERO) > 0) {
                                    payments.setAmount(sumAmount);
                                    String partial = Backend.STATUS_PAYMENTS_PARTIAL.getBackend();
                                    payments.setStatus(partial);
                                    payments.setPayments_method(paymentsMethod);
                                    orderbackendMapper.updatePayments(payments);
                                    messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(userPaymentsStatus));
                                    shipments.setPaymentsStatuss(List.of(partial));
                                } else {
                                    payments.setAmount(sumAmount);
                                    String paid = Backend.STATUS_PAYMENTS_PAID.getBackend();
                                    payments.setStatus(paid);
                                    payments.setPayments_method(paymentsMethod);
                                    orderbackendMapper.updatePayments(payments);
                                    messageGroup.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paid));
                                    shipments.setPaymentsStatuss(List.of(paid));
                                }
                                shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                                userPaymentsMethod = shipmentsData.getFirst().get("payments_method").toString();
                                userPaymentsAmount = shipmentsData.getFirst().get("payments_amount").toString();
                                userOrdersTotalPrice = shipmentsData.getFirst().get("orders_total_price").toString();
                            }
                            messageGroup.add("付款方法:" + StatusKey.paymentsMethodKey.get(userPaymentsMethod));
                            if (doNotChange) {
                                messageGroup.add("----------請勿更改(" +
                                        StatusKey.paymentsMethodKey.get(paymentsMethod) + ")付款方式----------");
                            }
                            messageGroup.add("已付金額:" + userPaymentsAmount);
                            messageGroup.add("需付款金額:" + userOrdersTotalPrice);
                            messageGroup.add("應付款金額:" +
                                    new BigDecimal(userOrdersTotalPrice).subtract(new BigDecimal(userPaymentsAmount)));
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "付款",
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
                        logger.error("{} : (付款)無效的 JWT token", username);
                        throw new BadRequestException(username + " - 無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : userPayMoney 資源忙碌，請重試", username);
                List<Object> messageList = List.of(
                        "帳號-" + username,
                        username + " - 付款，資源忙碌，請重試"
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
