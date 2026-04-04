package com.example.demo.Service;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.Context;
import com.example.demo.Common.ConvertFormat;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Orderbackend.QuotationsProductItemRequest;
import com.example.demo.Dto.Orderbackend.UserUser;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.User.*;
import com.example.demo.Exception.BadRequestException;
import com.example.demo.Exception.ResourceNotFoundException;
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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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

    public OrderbackendServiceImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            ProductMapper productMapper,
            OrderbackendMapper orderbackendMapper,
            StringRedisTemplate stringRedisTemplate) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.orderbackendMapper = orderbackendMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // <App>:<Domain>:<Purpose>:<ID>
    private final Map<String, String> redisKey = Map.of(
            "refresh", "user:jwt:refresh:{1}",
            "access", "user:jwt:access:{1}:{2}",
            "blacklist", "user:jwt:blacklist:{1}",
            "lock", "user:auth:lock:{1}",
            "fail", "user:auth:fail:{1}"
    );

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

    @Override
    public ResponseEntity<?> testLogin() {
        logger.info("orderbackend/testLogin: orderbackend is working!");
        List<Object> messageList = List.of("orderbackend is working!");
        Map<String, Map<Integer, Object>> message = Map.of(
                "content", ConvertFormat.convert(messageList)
        );
        HttpStatus status = HttpStatus.OK;
        return ResponseEntity
                .status(status)
                .body(ApiResponse.api(
                        status,
                        message
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
                    String lockKey = redisKey.get("lock").replace("{1}", username);
                    String failKey = redisKey.get("fail").replace("{1}", username);
                    if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
                        Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
                        logger.error("{} : 連續錯誤{}次，帳號暫時被鎖，請稍後再試({}秒)", username, maxFailAttempts, ttl);
                        throw new RuntimeException(username + " - 連續錯誤" + maxFailAttempts + "次，帳號暫時被鎖，請稍後再試(" + ttl + "秒)");
                    }
                    Map<String, Object> userSelect = getUserData(userData);
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
                    final String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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
                            LocalDateTime.now()
                    );
                    Map<String, Map<Integer, Object>> message = Map.of(
                            "content", ConvertFormat.convert(messageList)
                    );
                    HttpStatus status = HttpStatus.OK;
                    return ResponseEntity
                            .status(status)
                            .body(ApiResponse.api(
                                    status,
                                    message
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
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
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
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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

                        String blacklistRedisKey = redisKey.get("blacklist").replace("{1}", usernameJwtId);
                        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistRedisKey))) {
                            logger.error("{} : (Token驗證)Token 已被撤銷", username);
                            throw new RuntimeException(username + " - Token 已被撤銷");
                        }

                        Map<String, Object> userSelect = getUserData(userData);
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
                        String accessRedisKey = redisKey.get("access")
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
                        accessRedisKey = redisKey.get("access")
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
                                LocalDateTime.now()
                        );
                        Map<String, Map<Integer, Object>> message = new TreeMap<>();
                        message.put("content", ConvertFormat.convert(messageList));
                        message.put("token", ConvertFormat.convert(List.of(accessToken)));
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        message
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
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
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
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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
                        String blacklistRedisKey = redisKey.get("blacklist").replace("{1}", usernameAccessJwtId);
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

                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", "*")
                                .replace("{2}", usernameAccessJwt);
                        stringRedisTemplate.delete(accessRedisKey);

                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : 登出 Token 帳號不存在", username);
                            throw new ResourceNotFoundException(username + " - 登出 Token 帳號不存在");
                        }
                        String permissions = userSelect.get("permissions").toString();
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + permissions,
                                username + " - Token 已登出",
                                LocalDateTime.now()
                        );
                        Map<String, Map<Integer, Object>> message = new TreeMap<>();
                        message.put("content", ConvertFormat.convert(messageList));
                        message.put("token", ConvertFormat.convert(List.of("")));
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        message
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
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
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
        String blacklistRedisKey = redisKey.get("blacklist").replace("{1}", usernameAccessJwtId);
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
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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

                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢使用者名單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        Map<String, Object> userSelect = getUserData(userData);
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
                                "新增日期" + ((Timestamp) userSelect.get("created_date")).toLocalDateTime(),
                                "更改日期" + ((Timestamp) userSelect.get("updated_date")).toLocalDateTime()
                        );
                        Map<String, Map<Integer, Object>> message = Map.of(
                                "content", ConvertFormat.convert(messageList)
                        );
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        message
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
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
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
    private Map<String, BigDecimal> calculateByMargin(BigDecimal cost, double marginPercent) {
        Map<String, BigDecimal> map = new HashMap<>();

        // 1. 先算售價
        double rate = 1 - (marginPercent / 100);
        BigDecimal sellingPrice = cost
                .divide(BigDecimal.valueOf(rate), 0, RoundingMode.HALF_UP);

        // 2. 算利潤
        BigDecimal profit = sellingPrice
                .subtract(cost).setScale(0, RoundingMode.HALF_UP);

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
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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

                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (用戶商品報價) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (用戶商品報價) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();
                        UserUser userUser = new UserUser(useruser);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
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
                                messageGroup.add("訂購數量 - " + new BigDecimal(arr[1]));
                                BigDecimal price = new BigDecimal(productsSelect.getFirst().get("price").toString());
                                messageGroup.add("價格 - " + price);
                                int num = Integer.parseInt(userPercent);
                                Map<String, BigDecimal> queryQuotationsMap = calculateByMargin(price, num);
                                messageGroup.add("---------- " + num + "% ----------");
                                messageGroup.add("售價: " + queryQuotationsMap.get("sellingPrice").toString());
                                messageGroup.add("利潤: " + queryQuotationsMap.get("profit").toString());
                                messageGroup.add("利潤率: " + queryQuotationsMap.get("margin").toString() + "%");
                                messageGroup.add("---------- " + num + "% ----------");
                                messageGroup.add("描述 - " + productsSelect.getFirst().get("description").toString());
                                messageGroup.add("---------------------------------------");
                            }
                            productsList.add(messageGroup);
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "用戶商品報價",
                                ConvertFormat.convert(productsList)
                        );
                        Map<String, Map<Integer, Object>> message = Map.of(
                                "content", ConvertFormat.convert(messageList)
                        );
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        message
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
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
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
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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

                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (確認報價) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
                        if (userSelect == null) {
                            logger.error("{} : (確認報價) 使用者不存在", username);
                            throw new ResourceNotFoundException(username + " - 使用者不存在");
                        }
                        List<Object> productsList = new ArrayList<>();





                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "確認報價",
                                ConvertFormat.convert(productsList)
                        );
                        Map<String, Map<Integer, Object>> message = Map.of(
                                "content", ConvertFormat.convert(messageList)
                        );
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        message
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
                        username + " - 確認報價，資源忙碌，請重試"
                );
                Map<String, Map<Integer, Object>> message = Map.of(
                        "content", ConvertFormat.convert(messageList)
                );
                HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
                return ResponseEntity
                        .status(status)
                        .body(ApiResponse.api(
                                status,
                                message
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
