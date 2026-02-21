package com.example.demo.Service;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.ActionType;
import com.example.demo.Common.CertificateFunction;
import com.example.demo.Common.Context;
import com.example.demo.Dto.User.*;
import com.example.demo.Exception.*;
import com.example.demo.Mapper.SecretMapper;
import com.example.demo.Mapper.UserMapper;
import com.example.demo.Security.Annotation.CheckRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Service
public class UserServiceImpl implements UserService {

    private final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    @Resource
    private SecretMapper secretMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final Map<String, String> redisKey = Map.of(
            "refresh", "refresh:{1}",
            "access", "access:{1}:{2}",
            "lock", "lock:{1}",
            "fail", "fail:{1}"
    );

    private final String keystorePath = "user-keystorePath";

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
    @Transactional
    @PermitAll
    public ResponseEntity<?> takeToken(UserRequest request) {
        final String username = request.getUsername();
        final String password = request.getPassword();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User takeToken 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "takeToken");
                    if (!valid) {
                        logger.error("takeToken 憑證未通過");
                        userData.setMessage("Token 憑證未通過");
                        userData.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userData.getStatus())
                                .body(new UserResponse(userData));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (Token 取得)帳號不存在", username);
                        throw new ResourceNotFoundException("帳號不存在");
                    }
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
                        throw new LockedException("連續錯誤" + maxFailAttempts + "次，帳號暫時被鎖，請稍後再試(" + ttl + "秒)");
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
                        throw new ResourceNotFoundException("帳號密碼錯誤，第" + failCount + "次。共可輸入" + maxFailAttempts + "次");
                    }
                    stringRedisTemplate.delete(failKey);
                    stringRedisTemplate.delete(lockKey);

                    userData.setPermissions(permissions);
                    userData.setCreated_date(((Timestamp) userSelect.get("created_date")).toLocalDateTime());
                    userData.setUpdated_date(((Timestamp) userSelect.get("updated_date")).toLocalDateTime());
                    // JWT 簽名與驗證用的「祕密字串（secret）」
                    final String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    final String refreshToken = Jwts.builder()
                            .setSubject(username)
                            .setIssuedAt(new Date())
                            .setExpiration(
                                    Date.from(
                                            Instant.now().plus(expirationSeconds, ChronoUnit.SECONDS)
                                    )
                            )
                            .signWith(getKeyForToday())
                            .compact();
                    final Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                            refreshRedisKey,
                            refreshToken,
                            expirationSeconds,
                            TimeUnit.SECONDS
                    );
                    if (!success) {
                        throw new IllegalStateException("Token 已經存在");
                    }
                    logger.info("{} : (Token 取得)成功", username);
                    userData.setMessage("Token 取得成功");
                    userData.setStatus(HttpStatus.OK);
                    userData.setToken("");
                    userData.setData(new ArrayList<>());

                    return ResponseEntity
                            .status(userData.getStatus())
                            .body(new UserResponse(userData));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : takeToken 資源忙碌，請重試", username);
                userData.setMessage("資源忙碌，請重試");
                userData.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userData.getStatus())
                        .body(new UserResponse(userData));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    @PermitAll
    public ResponseEntity<?> validate(UserTokenValidateRequest request) {
        final String username = request.getUsername();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User validate 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "validate");
                    if (!valid) {
                        logger.error("validate 憑證未通過");
                        userData.setMessage("Token 憑證未通過");
                        userData.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userData.getStatus())
                                .body(new UserResponse(userData));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (Token驗證)帳號不存在", username);
                        throw new ResourceNotFoundException("帳號不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (Token驗證)不存在或已過期，請重新取得 Token", username);
                        throw new BadRequestException("Token 不存在或已過期，請重新取得 Token");
                    }
                    try {
                        String refreshTokenInRedis = stringRedisTemplate.opsForValue().get(refreshRedisKey);
                        Claims claims = Jwts.parserBuilder()
                                .setSigningKey(getKeyForToday())  // 你生成 token 時用的密鑰
                                .build()
                                .parseClaimsJws(refreshTokenInRedis)
                                .getBody();
                        String usernameJwt = claims.getSubject();
                        logger.info("{} : (Token驗證)有效的 JWT token", usernameJwt);

                        // JWT 簽名與驗證用的「祕密字串（secret）」
                        final String permissions = userSelect.get("permissions").toString();
                        String jti = UUID.randomUUID().toString();
                        String accessToken = Jwts.builder()
                                .setId(jti)
                                .setSubject(usernameJwt)
                                .claim("roles", permissions)
                                .setIssuedAt(new Date())
                                .setExpiration(
                                        Date.from(
                                                Instant.now().plus(expirationSeconds, ChronoUnit.SECONDS)
                                        )
                                )
                                .signWith(getKeyForToday())
                                .compact();
                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", "*")
                                .replace("{2}", usernameJwt);
                        ScanOptions options = ScanOptions.scanOptions()
                                .match(accessRedisKey)
                                .count(10)
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
                            // Redis「我希望每次 SCAN 返回大約 10 個 key」
                            // 這是一個 建議值，Redis 可能返回多於或少於這個數量，取決於內部算法。
                            int cnt = 10;
                            redisDels(accessRedisKey, cnt);
                        }
                        accessRedisKey = redisKey.get("access")
                                .replace("{1}", jti)
                                .replace("{2}", usernameJwt);
                        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                                accessRedisKey,
                                accessToken,
                                expirationSeconds,
                                TimeUnit.SECONDS
                        );
                        if (!success) {
                            logger.info("{} : (Token驗證)已經存在", usernameJwt);
                        } else {
                            logger.info("{} : (Token驗證)不存在", usernameJwt);
                        }
                        logger.info("{} : (Token驗證)成功", usernameJwt);
                        userData.setMessage("驗證成功");
                        userData.setStatus(HttpStatus.OK);
                        userData.setToken(accessToken);
                        userData.setPermissions(permissions);
                        userData.setCreated_date(((Timestamp) userSelect.get("created_date")).toLocalDateTime());
                        userData.setUpdated_date(((Timestamp) userSelect.get("updated_date")).toLocalDateTime());
                        userData.setData(new ArrayList<>());

                        return ResponseEntity
                                .status(userData.getStatus())
                                .body(new UserResponse(userData));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (Token驗證)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : validate 資源忙碌，請重試", username);
                userData.setMessage("資源忙碌，請重試");
                userData.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userData.getStatus())
                        .body(new UserResponse(userData));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    @PermitAll
    public ResponseEntity<?> logout(QueryUserRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        UserData userData = new UserData(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User logout 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "logout");
                    if (!valid) {
                        logger.error("logout 憑證未通過");
                        userData.setMessage("Token 憑證未通過");
                        userData.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userData.getStatus())
                                .body(new UserResponse(userData));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : 登出 Token 帳號不存在", username);
                        throw new ResourceNotFoundException("登出 Token 帳號不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (Token登出)不存在或已過期", username);
                        throw new BadRequestException("Token 不存在或已過期");
                    }
                    try {
                        String refreshTokenInRedis = stringRedisTemplate.opsForValue().get(refreshRedisKey);
                        SecretKey keyForToday = getKeyForToday();
                        Jwts.parserBuilder()
                                .setSigningKey(keyForToday)  // 你生成 token 時用的密鑰
                                .build()
                                .parseClaimsJws(refreshTokenInRedis);

                        Claims accessClaims = Jwts.parserBuilder()
                                .setSigningKey(keyForToday)  // 你生成 token 時用的密鑰
                                .build()
                                .parseClaimsJws(token)
                                .getBody();
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(Token登出)使用者錯誤");
                            throw new RuntimeException("使用者錯誤");
                        }
                        Boolean refreshExisted = stringRedisTemplate.delete(refreshRedisKey);
                        logger.info("{} : refresh Token {}",
                                username,
                                Boolean.TRUE.equals(refreshExisted)
                                        ? " : 成功登出 Token 已刪除"
                                        : " : 本來就不存在或已過期");
                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", "*")
                                .replace("{2}", usernameAccessJwt);
                        // Redis「我希望每次 SCAN 返回大約 10 個 key」
                        // 這是一個 建議值，Redis 可能返回多於或少於這個數量，取決於內部算法。
                        int cnt = 10;
                        redisDels(accessRedisKey, cnt);
                        userData.setMessage("已登出");
                        userData.setStatus(HttpStatus.OK);
                        String permissions = userSelect.get("permissions").toString();
                        userData.setPermissions(permissions);
                        userData.setCreated_date(((Timestamp) userSelect.get("created_date")).toLocalDateTime());
                        userData.setUpdated_date(((Timestamp) userSelect.get("updated_date")).toLocalDateTime());
                        userData.setToken("");
                        userData.setData(new ArrayList<>());

                        return ResponseEntity
                                .status(userData.getStatus())
                                .body(new UserResponse(userData));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (Token登出)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : logout 資源忙碌，請重試", username);
                userData.setMessage("資源忙碌，請重試");
                userData.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userData.getStatus())
                        .body(new UserResponse(userData));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
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

    private Claims tokenInRedis(String redisKey, String token) {
        String tokenInRedis = stringRedisTemplate.opsForValue().get(redisKey);
        SecretKey key = getKeyForToday();
        Jwts.parserBuilder()
                .setSigningKey(key)  // 你生成 token 時用的密鑰
                .build()
                .parseClaimsJws(tokenInRedis);
        return Jwts.parserBuilder()
                .setSigningKey(key)  // 你生成 token 時用的密鑰
                .build()
                .parseClaimsJws(token)
                .getBody();
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
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "queryUser");
                    if (!valid) {
                        logger.error("queryUser 憑證未通過");
                        userData.setMessage("queryUser 憑證未通過");
                        userData.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userData.getStatus())
                                .body(new UserResponse(userData));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (查詢使用者名單) 查使用者帳號不存在", username);
                        throw new ResourceNotFoundException("查使用者帳號不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (查詢使用者名單) Token 不存在或已過期", username);
                        throw new BadRequestException("Token 不存在或已過期");
                    }
                    try {
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢使用者名單)使用者錯誤");
                            throw new RuntimeException("(查詢使用者名單)使用者錯誤");
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
                            throw new BadRequestException("Token 已過期");
                        }
                        String accessTokenInRedis = stringRedisTemplate.opsForValue().get(accessRedisKey);
                        userData.setToken(accessTokenInRedis);
                        userData.setStatus(HttpStatus.OK);
                        userData.setCreated_date(((Timestamp) userSelect.get("created_date")).toLocalDateTime());
                        userData.setUpdated_date(((Timestamp) userSelect.get("updated_date")).toLocalDateTime());
                        String permissions = userSelect.get("permissions").toString();
                        userData.setPermissions(permissions);
                        userData.setMessage("");
                        List<String> isUserName = userMapper.queryUserName();
                        userData.setData(isUserName);

                        return ResponseEntity
                                .status(userData.getStatus())
                                .body(new UserResponse(userData));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (查詢使用者名單)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : queryUser 資源忙碌，請重試", username);
                userData.setMessage("資源忙碌，請重試");
                userData.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userData.getStatus())
                        .body(new UserResponse(userData));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
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
    @CheckRole(Permissions.ORDER_ITEM_CREATE)
    public ResponseEntity<?> createOrderItem(CreateOrderItemRequest request) {
        final String username = request.getUsername();
        List<String> order_item = request.getOrder_item();
        final String token = request.getToken();
        UserData userData = new UserData(username);
        UserdataDetails userdataDetails = new UserdataDetails(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User createOrderItem 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "createOrderItem");
                    if (!valid) {
                        logger.error("createOrderItem 憑證未通過");
                        userdataDetails.setMessage("createOrderItem 憑證未通過");
                        userdataDetails.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userdataDetails.getStatus())
                                .body(new UserdataDetailsResponse(userdataDetails));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (新增訂單) 使用者不存在", username);
                        throw new ResourceNotFoundException("使用者不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (新增訂單) Token 不存在或已過期", username);
                        throw new BadRequestException("Token 不存在或已過期");
                    }
                    try {
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(新增訂單)使用者錯誤");
                            throw new RuntimeException("(新增訂單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (新增訂單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(新增訂單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);
                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (新增訂單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException("Token 已過期");
                        }
                        try {
                            String accessTokenInRedis = stringRedisTemplate.opsForValue().get(accessRedisKey);
                            userdataDetails.setToken(accessTokenInRedis);
                            String permissions = userSelect.get("permissions").toString();
                            userdataDetails.setPermissions(permissions);
                            userdataDetails.setOrder_item(order_item);
                            userdataDetails.setOrder_item_str(order_item.toString());
                            userMapper.createUserdataDetail(userdataDetails);
                            userdataDetails.setStatus(HttpStatus.OK);
                            logger.info("dataDetails 新增訂單成功");
                            userdataDetails.setMessage("新增訂單成功");
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            userdataDetails.setCreated_date(((Timestamp) userdataDetailsSelect.get("created_date")).toLocalDateTime());
                            userdataDetails.setUpdated_date(((Timestamp) userdataDetailsSelect.get("updated_date")).toLocalDateTime());
                            userdataDetails.setAction_type(ActionType.INSERT.name());
                            userMapper.createUserdataDetailU(userdataDetails);
                            userdataDetails.setHistory(new ArrayList<>());

                            return ResponseEntity
                                    .status(userdataDetails.getStatus())
                                    .body(new UserdataDetailsResponse(userdataDetails));
                        } catch (DuplicateKeyException e) {
                            logger.warn("新增訂單已存在，username={}", usernameAccessJwt);
                            throw new ResourceAlreadyExistsException("新增訂單已存在", e);
                        } catch (DataIntegrityViolationException e) {
                            logger.warn("新增訂單資料不合法，username={}", usernameAccessJwt);
                            throw new IsViolationException("新增訂單資料不合法", e);
                        } catch (DataAccessException e) {
                            logger.error("新增資料庫錯誤，username={}", usernameAccessJwt);
                            throw new DBException("系統錯誤，請稍後再試", e);
                        }
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (新增訂單)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : createOrderItem 資源忙碌，請重試", username);
                userdataDetails.setMessage("資源忙碌，請重試");
                userdataDetails.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userdataDetails.getStatus())
                        .body(new UserdataDetailsResponse(userdataDetails));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional
    @CheckRole(Permissions.ORDER_ITEM_QUERY)
    public ResponseEntity<?> queryOrderItem(QueryOrderItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        UserData userData = new UserData(username);
        UserdataDetails userdataDetails = new UserdataDetails(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User queryOrderItem 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "queryOrderItem");
                    if (!valid) {
                        logger.error("queryOrderItem 憑證未通過");
                        userdataDetails.setMessage("queryOrderItem 憑證未通過");
                        userdataDetails.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userdataDetails.getStatus())
                                .body(new UserdataDetailsResponse(userdataDetails));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (查詢訂單) 使用者不存在", username);
                        throw new ResourceNotFoundException("使用者不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (查詢訂單) Token 不存在或已過期", username);
                        throw new BadRequestException("Token 不存在或已過期");
                    }
                    try {
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(查詢訂單)使用者錯誤");
                            throw new RuntimeException("(查詢訂單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (查詢訂單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(查詢訂單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);
                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (查詢訂單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException("Token 已過期");
                        }
                        String accessTokenInRedis = stringRedisTemplate.opsForValue().get(accessRedisKey);
                        userdataDetails.setToken(accessTokenInRedis);

                        String permissions = userSelect.get("permissions").toString();
                        if (Stream.of("ADMIN", "MANAGER").anyMatch(permissions::contains)) {
                            List<String> isUser = userMapper.queryUserSelect();
                            if (isUser.isEmpty()) {
                                logger.error("{} : {} (查詢訂單) 訂單不存在", username, permissions);
                                throw new ResourceNotFoundException("訂單不存在");
                            }
                            List<String> isUserNew = new ArrayList<>();
                            for (int i = 0; i < isUser.size(); i++) {
                                String data = isUser.get(i);
                                logger.info("{} : (查詢訂單) {}", username, data);
                                String[] split = data.split("\\*\\|");
                                isUserNew.add((i + 1) + "." + split[0]);
                                String[] strArray = split[1]
                                        .replaceAll("[\\[\\] ]", "")
                                        .split(",");
                                isUserNew.addAll(Arrays.asList(strArray));
                                isUserNew.add("--------------------");
                            }
                            userdataDetails.setOrder_item(isUserNew);
                            userdataDetails.setCreated_date(LocalDateTime.now());
                            userdataDetails.setUpdated_date(LocalDateTime.now());
                            userdataDetails.setHistory(new ArrayList<>());
                        } else {
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (userdataDetailsSelect == null) {
                                logger.error("{} : (查詢訂單) 訂單不存在", username);
                                throw new ResourceNotFoundException("訂單不存在");
                            }
                            if (userdataDetailsSelect.get("order_item") != null) {
                                String[] strArray = userdataDetailsSelect.get("order_item")
                                        .toString()
                                        .replaceAll("[\\[\\] ]", "")
                                        .split(",");
                                userdataDetails.setOrder_item(Arrays.asList(strArray));
                            } else {
                                userdataDetails.setOrder_item(new ArrayList<>());
                            }
                            userdataDetails.setCreated_date(
                                    ((Timestamp) userdataDetailsSelect.get("created_date")).toLocalDateTime());
                            userdataDetails.setUpdated_date(
                                    ((Timestamp) userdataDetailsSelect.get("updated_date")).toLocalDateTime());
                            userdataDetails.setHistory(new ArrayList<>());
                        }
                        userdataDetails.setPermissions(permissions);
                        logger.info("dataDetails 查詢訂單成功");
                        userdataDetails.setMessage("查詢訂單成功");
                        userdataDetails.setStatus(HttpStatus.OK);
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (查詢訂單)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                    return ResponseEntity
                            .status(userdataDetails.getStatus())
                            .body(new UserdataDetailsResponse(userdataDetails));
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : queryOrderItem 資源忙碌，請重試", username);
                userdataDetails.setMessage("資源忙碌，請重試");
                userdataDetails.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userdataDetails.getStatus())
                        .body(new UserdataDetailsResponse(userdataDetails));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @CheckRole(Permissions.ORDER_ITEM_UPDATE)
    public ResponseEntity<?> updateOrderItem(UpdateOrderItemRequest request) {
        final String username = request.getUsername();
        List<String> order_item = request.getOrder_item();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        UserData userData = new UserData(username);
        UserdataDetails userdataDetails = new UserdataDetails(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User updateOrderItem 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "updateOrderItem");
                    if (!valid) {
                        logger.error("updateOrderItem 憑證未通過");
                        userdataDetails.setMessage("updateOrderItem 憑證未通過");
                        userdataDetails.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userdataDetails.getStatus())
                                .body(new UserdataDetailsResponse(userdataDetails));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (更改訂單) 使用者不存在", username);
                        throw new ResourceNotFoundException("使用者不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (更改訂單) Token 不存在或已過期", username);
                        throw new BadRequestException("Token 不存在或已過期");
                    }
                    try {
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(更改訂單)使用者錯誤");
                            throw new RuntimeException("(更改訂單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (更改訂單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(更改訂單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);
                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (更改訂單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException("Token 已過期");
                        }
                        try {
                            String accessTokenInRedis = stringRedisTemplate.opsForValue().get(accessRedisKey);
                            userdataDetails.setToken(accessTokenInRedis);

                            String permissions = userSelect.get("permissions").toString();
                            if (Stream.of("ADMIN", "MANAGER").anyMatch(permissions::contains)) {
                                logger.info("{}(更改訂單) : ADMIN、MANAGER", useruser);
                                userdataDetails.setUsername(useruser);
                            }
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (userdataDetailsSelect == null) {
                                logger.error("{} : (更改訂單) 訂單不存在", username);
                                throw new ResourceNotFoundException("訂單不存在");
                            }

                            userdataDetails.setPermissions(userdataDetailsSelect.get("permissions").toString());
                            userdataDetails.setOrder_item(order_item);
                            userdataDetails.setOrder_item_str(order_item.toString());
                            userMapper.updateUserdataDetail(userdataDetails);
                            userdataDetails.setStatus(HttpStatus.OK);
                            logger.info("dataDetails 更改訂單成功");
                            userdataDetails.setMessage("更改訂單成功");
                            userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            userdataDetails.setCreated_date(((Timestamp) userdataDetailsSelect.get("created_date")).toLocalDateTime());
                            userdataDetails.setUpdated_date(((Timestamp) userdataDetailsSelect.get("updated_date")).toLocalDateTime());
                            userdataDetails.setAction_type(ActionType.UPDATE.name());
                            userMapper.createUserdataDetailU(userdataDetails);
                            userdataDetails.setHistory(new ArrayList<>());
                            return ResponseEntity
                                    .status(userdataDetails.getStatus())
                                    .body(new UserdataDetailsResponse(userdataDetails));
                        } catch (DataIntegrityViolationException e) {
                            logger.warn("更改訂單資料不合法，username={}", usernameAccessJwt);
                            throw new IsViolationException("更改訂單資料不合法", e);
                        } catch (DataAccessException e) {
                            logger.error("更改資料庫錯誤，username={}", usernameAccessJwt);
                            throw new DBException("系統錯誤，請稍後再試", e);
                        }
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (更改訂單)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : updateOrderItem 資源忙碌，請重試", username);
                userdataDetails.setMessage("資源忙碌，請重試");
                userdataDetails.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userdataDetails.getStatus())
                        .body(new UserdataDetailsResponse(userdataDetails));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @CheckRole(Permissions.ORDER_ITEM_DELETE)
    public ResponseEntity<?> deleteOrderItem(DeleteOrderItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        UserData userData = new UserData(username);
        UserdataDetails userdataDetails = new UserdataDetails(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User deleteOrderItem 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "deleteOrderItem");
                    if (!valid) {
                        logger.error("deleteOrderItem 憑證未通過");
                        userdataDetails.setMessage("deleteOrderItem 憑證未通過");
                        userdataDetails.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userdataDetails.getStatus())
                                .body(new UserdataDetailsResponse(userdataDetails));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (刪除訂單) 使用者不存在", username);
                        throw new ResourceNotFoundException("使用者不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (刪除訂單) Token 不存在或已過期", username);
                        throw new BadRequestException("Token 不存在或已過期");
                    }
                    try {
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(刪除訂單)使用者錯誤");
                            throw new RuntimeException("(刪除訂單)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (刪除訂單)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(刪除訂單)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);
                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (刪除訂單) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException("Token 已過期");
                        }

                        try {
                            String accessTokenInRedis = stringRedisTemplate.opsForValue().get(accessRedisKey);
                            userdataDetails.setToken(accessTokenInRedis);

                            String permissions = userSelect.get("permissions").toString();
                            if (Stream.of("ADMIN", "MANAGER").anyMatch(permissions::contains)) {
                                logger.info("{}(訂單刪除) : ADMIN、MANAGER", useruser);
                                userdataDetails.setUsername(useruser);
                            }
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (userdataDetailsSelect == null) {
                                logger.error("{} : (刪除訂單) 訂單不存在", username);
                                throw new ResourceNotFoundException("訂單不存在");
                            }
                            userdataDetails.setPermissions(userdataDetailsSelect.get("permissions").toString());
                            userdataDetails.setOrder_item(new ArrayList<>());
                            userdataDetails.setOrder_item_str(new ArrayList<>().toString());
                            userMapper.deleteUserdataDetail(userdataDetails);
                            logger.info("dataDetails 訂單刪除成功");
                            userdataDetails.setMessage("訂單刪除成功");
                            userdataDetails.setStatus(HttpStatus.OK);
                            userdataDetails.setCreated_date(((Timestamp) userdataDetailsSelect.get("created_date")).toLocalDateTime());
                            userdataDetails.setUpdated_date(((Timestamp) userdataDetailsSelect.get("updated_date")).toLocalDateTime());
                            userdataDetails.setAction_type(ActionType.DELETE.name());
                            userMapper.createUserdataDetailU(userdataDetails);
                            userdataDetails.setHistory(new ArrayList<>());
                        } catch (DataIntegrityViolationException e) {
                            logger.warn("刪除訂單資料不合法，username={}", usernameAccessJwt);
                            throw new IsViolationException("刪除訂單資料不合法", e);
                        } catch (DataAccessException e) {
                            logger.error("刪除資料庫錯誤，username={}", usernameAccessJwt);
                            throw new DBException("系統錯誤，請稍後再試", e);
                        }
                        return ResponseEntity
                                .status(userdataDetails.getStatus())
                                .body(new UserdataDetailsResponse(userdataDetails));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (刪除訂單)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : deleteOrderItem 資源忙碌，請重試", username);
                userdataDetails.setMessage("資源忙碌，請重試");
                userdataDetails.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userdataDetails.getStatus())
                        .body(new UserdataDetailsResponse(userdataDetails));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @CheckRole(Permissions.ORDER_ITEM_HISTORY)
    public ResponseEntity<?> historyOrderItem(QueryOrderItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        UserData userData = new UserData(username);
        UserdataDetails userdataDetails = new UserdataDetails(username);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("User historyOrderItem 拿鎖");
                try {
                    boolean valid = CertificateFunction.certificateCheck(keystorePath, "historyOrderItem");
                    if (!valid) {
                        logger.error("historyOrderItem 憑證未通過");
                        userdataDetails.setMessage("historyOrderItem 憑證未通過");
                        userdataDetails.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                        return ResponseEntity
                                .status(userdataDetails.getStatus())
                                .body(new UserdataDetailsResponse(userdataDetails));
                    }
                    Map<String, Object> userSelect = getUserData(userData);
                    if (userSelect == null) {
                        logger.error("{} : (歷史紀錄) 使用者不存在", username);
                        throw new ResourceNotFoundException("使用者不存在");
                    }
                    String refreshRedisKey = redisKey.get("refresh").replace("{1}", username);
                    Boolean exists = stringRedisTemplate.hasKey(refreshRedisKey);
                    if (Boolean.FALSE.equals(exists)) {
                        logger.error("{} : (歷史紀錄) Token 不存在或已過期", username);
                        throw new BadRequestException("Token 不存在或已過期");
                    }
                    try {
                        Claims accessClaims = tokenInRedis(refreshRedisKey, token);
                        String usernameAccessJwt = accessClaims.getSubject();
                        if (!username.equals(usernameAccessJwt)) {
                            logger.error("(歷史紀錄)使用者錯誤");
                            throw new RuntimeException("(歷史紀錄)使用者錯誤");
                        }
                        String accessRole = accessClaims.get("roles", String.class);
                        String accessJti = accessClaims.getId();
                        logger.info("{}(權限{}) : (歷史紀錄)有效的 JWT token {}",
                                usernameAccessJwt, accessRole, accessJti);
                        String method = Context.get().get("method").toString();
                        String permissionsContext = Context.get().get("permissions").toString();
                        String descriptionContext = Context.get().get("description").toString();
                        String roles = Context.get().get("roles").toString();
                        logger.info("(歷史紀錄)(使用者[{}])(方法名稱[{}])(使用者權限[{}])(方法權限[{}])([{}])",
                                usernameAccessJwt, method, permissionsContext, descriptionContext, roles);
                        String accessRedisKey = redisKey.get("access")
                                .replace("{1}", accessJti)
                                .replace("{2}", usernameAccessJwt);
                        Boolean accessExists = stringRedisTemplate.hasKey(accessRedisKey);
                        if (Boolean.FALSE.equals(accessExists)) {
                            logger.error("{} : (歷史紀錄) Token 已過期", usernameAccessJwt);
                            throw new BadRequestException("Token 已過期");
                        }
                        String accessTokenInRedis = stringRedisTemplate.opsForValue().get(accessRedisKey);
                        userdataDetails.setToken(accessTokenInRedis);
                        String permissions = userSelect.get("permissions").toString();
                        userdataDetails.setPermissions(permissions);
                        logger.info("dataDetails 歷史紀錄查詢成功");
                        userdataDetails.setMessage("歷史紀錄查詢成功");
                        userdataDetails.setStatus(HttpStatus.OK);
                        userdataDetails.setCreated_date(((Timestamp) userSelect.get("created_date")).toLocalDateTime());
                        userdataDetails.setUpdated_date(((Timestamp) userSelect.get("updated_date")).toLocalDateTime());
                        userdataDetails.setOrder_item(new ArrayList<>());

                        List<String> item = userMapper.selectUserdataDetailUUsernameItem();
                        List<String> historys = new ArrayList<>();
                        for(String itemName : item) {
                            userdataDetails.setUsername(itemName);
                            List<String> list = userMapper.selectUserdataDetailU(userdataDetails);
                            for (int i = 0; i < list.size(); i++) {
                                String data = list.get(i);
                                logger.info("{} : (歷史紀錄) {}", username, data);
                                String[] split = data.split("\\*\\|");
                                historys.add((i + 1) + "." + split[0]);
                                String[] strArray = split[1]
                                        .replaceAll("[\\[\\] ]", "")
                                        .split(",");
                                historys.addAll(Arrays.asList(strArray));
                                historys.add("--------------------");
                            }
                        }
                        userdataDetails.setHistory(historys);
                        // 目前的使用者
                        userdataDetails.setUsername(usernameAccessJwt);

                        return ResponseEntity
                                .status(userdataDetails.getStatus())
                                .body(new UserdataDetailsResponse(userdataDetails));
                    } catch (JwtException e) {
                        // JWT 不合法
                        logger.error("{} : (歷史紀錄)無效的 JWT token", username);
                        throw new BadRequestException("無效的 JWT token", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : historyOrderItem 資源忙碌，請重試", username);
                userdataDetails.setMessage("資源忙碌，請重試");
                userdataDetails.setStatus(HttpStatus.TOO_MANY_REQUESTS);
                return ResponseEntity
                        .status(userdataDetails.getStatus())
                        .body(new UserdataDetailsResponse(userdataDetails));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
