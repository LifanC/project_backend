package com.example.demo.Service;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.Backend;
import com.example.demo.Common.Context;
import com.example.demo.Common.ConvertFormat;
import com.example.demo.Common.QuotationsStatusKey;
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
                                int num = Integer.parseInt(userPercent);
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
                                    int num = Integer.parseInt(userPercent);
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
                                    messageGroup.add("┌-----第" + (i + 1) + "筆-----┐");
                                    messageGroup.add("用戶商品數量: " + userUserQuantity);
                                    messageGroup.add("用戶商品單品價格: " + price);
                                    messageGroup.add("用戶商品銷售單品價格: " + sellingPrice);
                                    messageGroup.add("用戶商品銷售合計: " + total);
                                    messageGroup.add("└-----第" + (i + 1) + "筆-----┘");
                                    productsList.add(messageGroup);
                                    QuotationItems quotationItems = new QuotationItems(decimal);
                                    quotationItems.setProduct_id(product_id);
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
                        username + " - 確認報價單，資源忙碌，請重試"
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
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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

                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (刪除報價) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
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
                                QuotationItems quotationItems = new QuotationItems(quotation_idDel);
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
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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

                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢報價單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
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
                        Map<String, List<Map<String, Object>>> groupListAll = new HashMap<>();
                        for (Map<String, Object> quotationData : quotationsData) {
                            String quotationId = quotationData.get("quotation_id").toString();
                            groupListAll
                                    .computeIfAbsent(quotationId, k -> new ArrayList<>())
                                    .add(quotationData);
                        }
                        for (Map.Entry<String, List<Map<String, Object>>> entry : groupListAll.entrySet()) {
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
                                messageGroup.add("┌-----第" + (i + 1) + "筆-----┐");
                                messageGroup.add("|報價單:商品");
                                messageGroup.add("|用戶名稱: " + useruser);
                                // estimate（預估） / sent（已送出） / accepted（客戶接受） / rejected（拒絕）
                                messageGroup.add("|狀態: " + QuotationsStatusKey.quotationsKey.get(statusQuery));
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
    public ResponseEntity<?> sendQuotationsProduct(SendQuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String userUserQuotationsId = request.getUserUserQuotationsId();
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("orderbackend sendQuotationsProduct 拿鎖");
                try {
                    try {
                        String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
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

                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (送出報價單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException(username + " - Token 已過期");
                        }
                        UserData userData = new UserData(username);
                        Map<String, Object> userSelect = getUserData(userData);
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
                        userDataDetails.setQuotation_id(new BigDecimal(String.valueOf(userUserQuotationsId)));
                        Map<String, Object> quotationsDataSend = getQuotationsDataSend(userDataDetails);
                        if (quotationsDataSend == null) {
                            productsList.add("報價單編號:" + userUserQuotationsId + ":送出失敗，無此報價單");
                        } else {
                            String statusSend = quotationsDataSend.get("status").toString();
                            if (Backend.STATUS_QUOTATIONS_ESTIMATE.getBackend().equals(statusSend)) {
                                orderbackendMapper.updateQuotations(userDataDetails);
                                productsList.add("報價單編號:" + userUserQuotationsId + ":送出成功");
                                productsList.add("報價單狀態:" + QuotationsStatusKey.quotationsKey.get(send));
                            } else {
                                productsList.add("報價單編號:" + userUserQuotationsId + ":已送出，勿重複送單");
                            }
                        }
                        List<Object> messageList = List.of(
                                "帳號 - " + username,
                                "權限 - " + userSelect.get("permissions").toString(),
                                "送出報價單",
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
