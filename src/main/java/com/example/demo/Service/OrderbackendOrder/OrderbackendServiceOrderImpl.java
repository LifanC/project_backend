package com.example.demo.Service.OrderbackendOrder;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.Backend;
import com.example.demo.Common.Context;
import com.example.demo.Common.RedisKey;
import com.example.demo.Common.StatusKey;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Dto.User.Orders;
import com.example.demo.Dto.User.UserData;
import com.example.demo.Exception.BadRequestException;
import com.example.demo.Exception.ResourceNotFoundException;
import com.example.demo.Mapper.OrderbackendMapper;
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
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OrderbackendServiceOrderImpl implements OrderbackendServiceOrder {

    private final Logger logger = LoggerFactory.getLogger(OrderbackendServiceOrderImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final OrderbackendMapper orderbackendMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public OrderbackendServiceOrderImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            OrderbackendMapper orderbackendMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
        this.orderbackendMapper = orderbackendMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
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
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("testLogin 拿鎖");
                try {
                    List<Map<String, Object>> data = new ArrayList<>();
                    Map<String, Object> dataMap = new TreeMap<>();
                    dataMap.put("status_name", "狀態");
                    dataMap.put("status", "orderbackend is working!");
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
                            throw new ResourceNotFoundException("空");
                        } else {
                            List<Map<String, Object>> data = new ArrayList<>();
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotation_id = ordersData.get(i).get("quotation_id").toString();
                                String status = ordersData.get(i).get("status").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "查詢用戶");
                                dataMap.put("useruser", quotationsUsername);
                                dataMap.put("orderId", order_id);
                                dataMap.put("quotationsId", quotation_id);
                                dataMap.put("state", StatusKey.ordersStatusKey.get(status));
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
                            throw new ResourceNotFoundException("空");
                        } else {
                            List<Map<String, Object>> data = new ArrayList<>();
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotation_id = ordersData.get(i).get("quotation_id").toString();
                                String status = ordersData.get(i).get("status").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "訂單");
                                dataMap.put("useruser", quotationsUsername);
                                dataMap.put("orderId", order_id);
                                dataMap.put("quotationsId", quotation_id);
                                dataMap.put("state", StatusKey.ordersStatusKey.get(status));
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
                        Orders orders = new Orders(new BigDecimal(orderId));
                        orders.setUsername(useruser);
                        orders.setQuotationsStatuss(List.of(Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()));
                        orders.setStatuss(List.of(Backend.STATUS_ORDERS_PENDING.getBackend()));
                        List<Map<String, Object>> ordersData = orderbackendMapper.selectOrdersData(orders);
                        if (ordersData.isEmpty()) {
                            throw new ResourceNotFoundException("空");
                        } else {
                            String confirmed = Backend.STATUS_ORDERS_CONFIRMED.getBackend();
                            List<Map<String, Object>> data = new ArrayList<>();
                            Map<String, Object> dataMap = new TreeMap<>();
                            dataMap.put("remark", "確認訂單");
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotation_id = ordersData.get(i).get("quotation_id").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                dataMap.put("useruser", quotationsUsername);
                                dataMap.put("orderId", order_id);
                                dataMap.put("quotationsId", quotation_id);
                                List<String> list = new ArrayList<>();
                                list.add("訂單狀態:" + StatusKey.ordersStatusKey.get(confirmed));
                                dataMap.put("details" + (i + 1), list);
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
                            dataMap.put("state", StatusKey.shipmentsStatusKey.get(preparing));

                            // *付款（payments）
                            Payments payments = new Payments(new BigDecimal(orderId));
                            String unpaid = Backend.STATUS_PAYMENTS_UNPAID.getBackend();
                            String cash = Backend.METHOD_PAYMENTS_CASH.getBackend();
                            payments.setAmount(BigDecimal.ZERO);
                            payments.setStatus(unpaid);
                            payments.setPayments_method(cash);
                            orderbackendMapper.createPayments(payments);
                            data.add(dataMap);

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
                        Orders orders = new Orders(new BigDecimal(orderId));
                        orders.setUsername(useruser);
                        orders.setQuotationsStatuss(List.of(Backend.STATUS_QUOTATIONS_ACCEPTED.getBackend()));
                        orders.setStatuss(List.of(Backend.STATUS_ORDERS_PENDING.getBackend()));
                        List<Map<String, Object>> ordersData = orderbackendMapper.selectOrdersData(orders);
                        if (ordersData.isEmpty()) {
                            throw new ResourceNotFoundException("空");
                        } else {
                            String cancelled = Backend.STATUS_ORDERS_CANCELLED.getBackend();
                            List<Map<String, Object>> data = new ArrayList<>();
                            Map<String, Object> dataMap = new TreeMap<>();
                            dataMap.put("remark", "取消訂單");
                            for (int i = 0; i < ordersData.size(); i++) {
                                String order_id = ordersData.get(i).get("order_id").toString();
                                String quotation_id = ordersData.get(i).get("quotation_id").toString();
                                String quotationsUsername = ordersData.get(i).get("username").toString();
                                dataMap.put("useruser", quotationsUsername);
                                dataMap.put("orderId", order_id);
                                dataMap.put("quotationsId", quotation_id);
                                List<String> list = new ArrayList<>();
                                list.add("訂單狀態:" + StatusKey.ordersStatusKey.get(cancelled));
                                dataMap.put("details" + (i + 1), list);
                            }
                            data.add(dataMap);
                            orders.setStatus(cancelled);
                            orderbackendMapper.updateOrders(orders);
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
