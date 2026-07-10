package com.example.demo.Service.OrderbackendShipment;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.Backend;
import com.example.demo.Common.Context;
import com.example.demo.Common.RedisKey;
import com.example.demo.Common.StatusKey;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.User.UserData;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OrderbackendServiceShipmentImpl implements OrderbackendServiceShipment {

    private final Logger logger = LoggerFactory.getLogger(OrderbackendServiceShipmentImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderbackendMapper orderbackendMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public OrderbackendServiceShipmentImpl(
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
                        if (shipmentsData.isEmpty()) {
                            throw new ResourceNotFoundException("空");
                        } else {
                            List<Map<String, Object>> data = new ArrayList<>();
                            for (int i = 0; i < shipmentsData.size(); i++) {
                                String order_id = shipmentsData.get(i).get("order_id").toString();
                                String quotation_id = shipmentsData.get(i).get("quotation_id").toString();
                                String shipmentsUsername = shipmentsData.get(i).get("username").toString();
                                String tracking_number = shipmentsData.get(i).get("tracking_number").toString();
                                String status = shipmentsData.get(i).get("status").toString();
                                String paymentsStatus = shipmentsData.get(i).get("payments_status").toString();
                                String paymentsMethod = shipmentsData.get(i).get("payments_method").toString();
                                String paymentsAmount = shipmentsData.get(i).get("payments_amount").toString();
                                String ordersTotalPrice = shipmentsData.get(i).get("orders_total_price").toString();
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "查詢用戶出貨名單");
                                dataMap.put("useruser", shipmentsUsername);
                                dataMap.put("orderId", order_id);
                                dataMap.put("quotationsId", quotation_id);
                                List<String> list = new ArrayList<>();
                                list.add("追蹤號碼:" + tracking_number);
                                list.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paymentsStatus));
                                list.add("付款方法:" + StatusKey.paymentsMethodKey.get(paymentsMethod));
                                list.add("已付金額:" + paymentsAmount);
                                list.add("需付款金額:" + ordersTotalPrice);
                                BigDecimal A = new BigDecimal(ordersTotalPrice);
                                BigDecimal B = new BigDecimal(paymentsAmount);
                                list.add("應付款金額:" + A.subtract(B));
                                dataMap.put("details" + (i + 1), list);
                                dataMap.put("state", StatusKey.shipmentsStatusKey.get(status));
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                                        Backend.STATUS_SHIPMENTS_PENDING.getBackend(),
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
                            throw new ResourceNotFoundException("空");
                        } else {
                            String order_id = shipmentsData.getFirst().get("order_id").toString();
                            String quotation_id = shipmentsData.getFirst().get("quotation_id").toString();
                            String shipmentsUsername = shipmentsData.getFirst().get("username").toString();
                            String tracking_number = shipmentsData.getFirst().get("tracking_number").toString();
                            String paymentsStatus = shipmentsData.getFirst().get("payments_status").toString();
                            String paymentsMethod = shipmentsData.getFirst().get("payments_method").toString();
                            String paymentsAmount = shipmentsData.getFirst().get("payments_amount").toString();
                            String ordersTotalPrice = shipmentsData.getFirst().get("orders_total_price").toString();
                            String preparing = Backend.STATUS_SHIPMENTS_PENDING.getBackend();
                            String shipped = Backend.STATUS_SHIPMENTS_SHIPPED.getBackend();
                            shipments.setStatus(shipped);
                            List<Map<String, Object>> data = new ArrayList<>();
                            Map<String, Object> dataMap = new TreeMap<>();
                            dataMap.put("remark", "已出貨");
                            dataMap.put("useruser", shipmentsUsername);
                            dataMap.put("orderId", order_id);
                            dataMap.put("quotationsId", quotation_id);
                            List<String> list = new ArrayList<>();
                            list.add("追蹤號碼:" + tracking_number);
                            list.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paymentsStatus));
                            list.add("付款方法:" + StatusKey.paymentsMethodKey.get(paymentsMethod));
                            list.add("已付金額:" + paymentsAmount);
                            list.add("需付款金額:" + ordersTotalPrice);
                            BigDecimal A = new BigDecimal(ordersTotalPrice);
                            BigDecimal B = new BigDecimal(paymentsAmount);
                            list.add("應付款金額:" + A.subtract(B));
                            String partial = Backend.STATUS_PAYMENTS_PARTIAL.getBackend();
                            boolean statusBoolean = partial.equals(paymentsStatus);
                            list.add(statusBoolean
                                    ? "--------未繳清金額--------"
                                    : "--------已繳清金額--------");
                            dataMap.put("details" + (1), list);
                            dataMap.put("state", (!statusBoolean)
                                    ? StatusKey.shipmentsStatusKey.get(shipped)
                                    : StatusKey.shipmentsStatusKey.get(preparing));
                            data.add(dataMap);
                            if (!statusBoolean) {
                                orderbackendMapper.updateShipments(shipments);
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                            throw new ResourceNotFoundException("空");
                        } else {
                            String order_id = shipmentsData.getFirst().get("order_id").toString();
                            String quotation_id = shipmentsData.getFirst().get("quotation_id").toString();
                            String shipmentsUsername = shipmentsData.getFirst().get("username").toString();
                            String tracking_number = shipmentsData.getFirst().get("tracking_number").toString();
                            String paymentsStatus = shipmentsData.getFirst().get("payments_status").toString();
                            String paymentsMethod = shipmentsData.getFirst().get("payments_method").toString();
                            String paymentsAmount = shipmentsData.getFirst().get("payments_amount").toString();
                            String ordersTotalPrice = shipmentsData.getFirst().get("orders_total_price").toString();
                            String delivered = Backend.STATUS_SHIPMENTS_DELIVERED.getBackend();
                            List<Map<String, Object>> data = new ArrayList<>();
                            Map<String, Object> dataMap = new TreeMap<>();
                            dataMap.put("remark", "已送達");
                            dataMap.put("useruser", shipmentsUsername);
                            dataMap.put("orderId", order_id);
                            dataMap.put("quotationsId", quotation_id);
                            List<String> list = new ArrayList<>();
                            list.add("追蹤號碼:" + tracking_number);
                            list.add("付款狀態:" + StatusKey.paymentsStatusKey.get(paymentsStatus));
                            list.add("付款方法:" + StatusKey.paymentsMethodKey.get(paymentsMethod));
                            list.add("已付金額:" + paymentsAmount);
                            list.add("需付款金額:" + ordersTotalPrice);
                            BigDecimal A = new BigDecimal(ordersTotalPrice);
                            BigDecimal B = new BigDecimal(paymentsAmount);
                            list.add("應付款金額:" + A.subtract(B));
                            dataMap.put("details" + (1), list);
                            dataMap.put("state", StatusKey.shipmentsStatusKey.get(delivered));
                            data.add(dataMap);
                            shipments.setStatus(delivered);
                            orderbackendMapper.updateShipments(shipments);
                            Map<String, Object> productsData =
                                    orderbackendMapper.selectProductsData(shipments).get(shipments.getTracking_number());
                            if (productsData != null) {
                                String productId = productsData.get("product_id").toString();
                                BigDecimal stock = new BigDecimal(productsData.get("stock").toString());
                                BigDecimal quantity = new BigDecimal(productsData.get("quantity").toString());
                                BigDecimal priceDifference = stock.subtract(quantity);
                                Product product = new Product(new BigDecimal(productId));
                                product.setStock(priceDifference);
                                productMapper.update(product);
                            }
                            shipments.setOrder_id(new BigDecimal(order_id));
                            String useruser = shipmentsData.getFirst().get("username").toString();
                            List<Map<String, Object>> orderItemDb = orderbackendMapper.orderItemDbData(useruser);
                            List<Map<String, Object>> orderItemData = orderbackendMapper.selectOrderItemData(shipments);
                            List<String> listSort2 = new ArrayList<>();
                            for (Map<String, Object> map : orderItemData) {
                                listSort2.add(map.get("order_item").toString());
                            }
                            Collections.sort(listSort2);
                            boolean same = false;
                            String sameOrderItem = "";
                            for (Map<String, Object> map: orderItemDb) {
                                String order_item = map.get("order_item").toString();
                                String[] splits = order_item.split(",");
                                if (orderItemData.size() == splits.length) {
                                    List<String> listSort1 = new ArrayList<>(Arrays.stream(splits).toList());
                                    Collections.sort(listSort1);
                                    List<Boolean> ok = new ArrayList<>(Collections.nCopies(listSort1.size(), false));
                                    for (int i = 0; i < listSort1.size(); i++) {
                                        if (listSort1.get(i).equals(listSort2.get(i))) {
                                            ok.set(i, true);
                                        }
                                    }
                                    same = ok.stream().allMatch(Boolean.TRUE::equals);
                                    if (same) {
                                        sameOrderItem = order_item;
                                        break;
                                    }
                                }
                            }
                            if (same) {
                                orderbackendMapper.updateUserdataDetailIsActiveNotificationsShow(
                                        tracking_number, useruser, sameOrderItem);
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
