package com.example.demo.Service.UserShipment;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.*;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Orderbackend.Shipments;
import com.example.demo.Dto.User.*;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
public class UserServiceShipmentImpl implements UserServiceShipment {

    private final Logger logger = LoggerFactory.getLogger(UserServiceShipmentImpl.class);

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final OrderbackendMapper orderbackendMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceShipmentImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            OrderbackendMapper orderbackendMapper,
            StringRedisTemplate stringRedisTemplate) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
        this.orderbackendMapper = orderbackendMapper;
        this.stringRedisTemplate = stringRedisTemplate;
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
                        shipments.setShipmentsStatuss(shipmentsStatusList);
                        shipments.setPaymentsStatuss(paymentsStatusList);
                        shipments.setPaymentsMethods(paymentsMethodList);
                        List<Map<String, Object>> shipmentsData = orderbackendMapper.selectShipmentsData(shipments);
                        if (shipmentsData.isEmpty()) {
                            throw new ResourceNotFoundException(username + " - 空");
                        } else {
                            List<Map<String, Object>> data = new ArrayList<>();
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
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "查詢出貨資訊");
                                dataMap.put("username", username);
                                dataMap.put("permissions", userSelect.get("permissions").toString());
                                dataMap.put("orderId", order_id);
                                List<String> list = new ArrayList<>();
                                list.add("訂單編號:" + order_id);
                                list.add("用戶:" + shipmentsUsername);
                                list.add("出貨日期:" + date);
                                list.add("追蹤號碼:" + tracking_number);
                                list.add("付款狀態:" + StatusKey.paymentsStatusKey.get(userPaymentsStatus));
                                list.add("付款方法:" + StatusKey.paymentsMethodKey.get(userPaymentsMethod));
                                list.add("已付金額:" + userPaymentsAmount);
                                list.add("需付款金額:" + userOrdersTotalPrice);
                                BigDecimal A = new BigDecimal(userOrdersTotalPrice);
                                BigDecimal B = new BigDecimal(userPaymentsAmount);
                                list.add("應付款金額:" + A.subtract(B));
                                dataMap.put("details" + (i + 1), list);
                                dataMap.put("state", StatusKey.shipmentsStatusKey.get(userStatus));
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
                        "帳號-" + username + " - 查詢出貨資訊，資源忙碌，請重試"
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
