package com.example.demo.Service.UserQuotation;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.*;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Orderbackend.Quotations;
import com.example.demo.Dto.Orderbackend.UserDataSend;
import com.example.demo.Dto.User.*;
import com.example.demo.Exception.BadRequestException;
import com.example.demo.Exception.ResourceNotFoundException;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UserServiceQuotationImpl implements UserServiceQuotation {

    private final Logger logger = LoggerFactory.getLogger(UserServiceQuotationImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public UserServiceQuotationImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
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
        logger.info("User/testLogin: User is working!");
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("testLogin 拿鎖");
                try {
                    List<Map<String, Object>> data = new ArrayList<>();
                    Map<String, Object> dataMap = new TreeMap<>();
                    dataMap.put("status_name", "狀態");
                    dataMap.put("status", "User is working!");
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
                logger.error("testLogin，請重試");
                List<Map<String, Object>> data = List.of(Map.of("remark", "testLogin，資源忙碌，請重試"));
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                            throw new ResourceNotFoundException(username + " - 空");
                        } else {
                            List<Map<String, Object>> data = new ArrayList<>();
                            for (int i = 0; i < quotationsIdData.size(); i++) {
                                String quotation_id = quotationsIdData.get(i).get("quotation_id").toString();
                                String status = quotationsIdData.get(i).get("status").toString();
                                BigDecimal total_price = new BigDecimal(quotationsIdData.get(i).get("total_price").toString());
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "報價單編號");
                                dataMap.put("username", username);
                                dataMap.put("permissions", userSelect.get("permissions").toString());
                                dataMap.put("quotation_id", quotation_id);
                                dataMap.put("state", StatusKey.quotationsStatusKey.get(status));
                                List<String> list = new ArrayList<>();
                                list.add(String.format("總金額(%s)", total_price));
                                dataMap.put("details" + (i + 1), list);
                                data.add(dataMap);
                            }
                            HttpStatus status = HttpStatus.OK;
                            return ResponseEntity
                                    .status(status)
                                    .body(ApiResponse.api(
                                            status,
                                            data
                                    ));
                        }
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
                        "帳號-" + username + " - 報價單，資源忙碌，請重試"
                );
                List<Map<String, Object>> data = List.of(Map.of("remark", messageList));
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                            throw new ResourceNotFoundException(username + " - 空");
                        } else {
                            List<Map<String, Object>> data = new ArrayList<>();
                            for (int i = 0; i < quotationsData.size(); i++) {
                                String status = quotationsData.get(i).get("status").toString();
                                BigDecimal quantity = new BigDecimal(quotationsData.get(i).get("quantity").toString());
                                BigDecimal price = new BigDecimal(quotationsData.get(i).get("price").toString());
                                BigDecimal sumPrice = new BigDecimal(quotationsData.get(i).get("sum_price").toString());
                                String productsName = quotationsData.get(i).get("products_name").toString();
                                String description = quotationsData.get(i).get("description").toString();
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "報價單");
                                dataMap.put("username", username);
                                dataMap.put("permissions", userSelect.get("permissions").toString());
                                dataMap.put("quotation_id", quotation_id);
                                List<String> list = new ArrayList<>();
                                list.add("數量 X 價格 = 合計");
                                list.add(String.format("%s X %s = %s", quantity, price, sumPrice));
                                list.add(String.format("名稱(%s)描述(%s)", productsName, description));
                                dataMap.put("details" + (i + 1), list);
                                dataMap.put("state", StatusKey.quotationsStatusKey.get(status));
                                data.add(dataMap);
                            }
                            HttpStatus status = HttpStatus.OK;
                            return ResponseEntity
                                    .status(status)
                                    .body(ApiResponse.api(
                                            status,
                                            data
                                    ));
                        }
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
                        "帳號-" + username + " - 報價單，資源忙碌，請重試"
                );
                List<Map<String, Object>> data = List.of(Map.of("remark", messageList));
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                        Quotations quotations = new Quotations(new BigDecimal(quotation_id));
                        quotations.setUsername(username);
                        Map<String, Object> quotationsDetails = userMapper.selectQuotations(quotations).get(username);
                        if (quotationsDetails == null) {
                            throw new ResourceNotFoundException(username + " - 空");
                        } else {
                            String quotationDetails_id = quotationsDetails.get("quotation_id").toString();
                            String statusDetails = quotationsDetails.get("status").toString();
                            String send = Backend.STATUS_QUOTATIONS_SENT.getBackend();
                            if (statusDetails.equals(send)) {
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
                                List<Map<String, Object>> data = new ArrayList<>();
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "接受");
                                dataMap.put("username", username);
                                dataMap.put("permissions", userSelect.get("permissions").toString());
                                dataMap.put("quotation_id", quotation_id);
                                for (int i = 0; i < getOrders.size(); i++) {
                                    Map<String, Object> getOrder = getOrders.get(i);
                                    String productsName = getOrder.get("products_name").toString();
                                    String description = getOrder.get("description").toString();
                                    List<String> list = new ArrayList<>();
                                    list.add(String.format("名稱(%s)描述(%s)", productsName, description));
                                    list.add(String.format("狀態(%s)", StatusKey.ordersStatusKey.get(pending)));
                                    dataMap.put("details" + (i + 1), list);
                                }
                                dataMap.put("state", StatusKey.quotationsStatusKey.get(accepted));
                                data.add(dataMap);
                                HttpStatus status = HttpStatus.OK;
                                return ResponseEntity
                                        .status(status)
                                        .body(ApiResponse.api(
                                                status,
                                                data
                                        ));
                            } else {
                                throw new ResourceNotFoundException(username + " - 空");
                            }
                        }
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
                        "帳號-" + username + " - 接受，資源忙碌，請重試"
                );
                List<Map<String, Object>> data = List.of(Map.of("remark", messageList));
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
                        Quotations quotations = new Quotations(new BigDecimal(quotation_id));
                        quotations.setUsername(username);
                        Map<String, Object> quotationsDetails = userMapper.selectQuotations(quotations).get(username);
                        if (quotationsDetails == null) {
                            throw new ResourceNotFoundException(username + " - 空");
                        } else {
                            String statusDetails = quotationsDetails.get("status").toString();
                            String send = Backend.STATUS_QUOTATIONS_SENT.getBackend();
                            if (statusDetails.equals(send)) {
                                UserDataSend userDataDetails = new UserDataSend(username);
                                String rejected = Backend.STATUS_QUOTATIONS_REJECTED.getBackend();
                                userDataDetails.setStatus(rejected);
                                userDataDetails.setQuotation_id(new BigDecimal(quotation_id));
                                userMapper.updateQuotations(userDataDetails);
                                List<Map<String, Object>> data = new ArrayList<>();
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "拒絕");
                                dataMap.put("username", username);
                                dataMap.put("permissions", userSelect.get("permissions").toString());
                                dataMap.put("quotation_id", quotation_id);
                                dataMap.put("state", StatusKey.quotationsStatusKey.get(rejected));
                                data.add(dataMap);
                                HttpStatus status = HttpStatus.OK;
                                return ResponseEntity
                                        .status(status)
                                        .body(ApiResponse.api(
                                                status,
                                                data
                                        ));
                            } else {
                                throw new ResourceNotFoundException(username + " - 空");
                            }
                        }
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
                        "帳號-" + username + " - 拒絕，資源忙碌，請重試"
                );
                List<Map<String, Object>> data = List.of(Map.of("remark", messageList));
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
