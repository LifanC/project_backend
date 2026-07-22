package com.example.demo.Service.OrderbackendQuotation;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.Backend;
import com.example.demo.Common.Context;
import com.example.demo.Common.RedisKey;
import com.example.demo.Common.StatusKey;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Notifications.NotificationMessage;
import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.User.UserData;
import com.example.demo.Exception.BadRequestException;
import com.example.demo.Exception.DBException;
import com.example.demo.Exception.IsViolationException;
import com.example.demo.Exception.ResourceNotFoundException;
import com.example.demo.Mapper.OrderbackendMapper;
import com.example.demo.Mapper.ProductMapper;
import com.example.demo.Mapper.SecretMapper;
import com.example.demo.Mapper.UserMapper;
import com.example.demo.Security.Annotation.CheckRole;
import com.example.demo.Service.Rabbitmq.RabbitService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class OrderbackendServiceQuotationImpl implements OrderbackendServiceQuotation {

    private final Logger logger = LoggerFactory.getLogger(OrderbackendServiceQuotationImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderbackendMapper orderbackendMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitService rabbitService;

    public OrderbackendServiceQuotationImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            ProductMapper productMapper,
            OrderbackendMapper orderbackendMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RabbitService rabbitService) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
        this.orderbackendMapper = orderbackendMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.rabbitService = rabbitService;
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

    private List<Map<String, Object>> getProduct(Product product) {
        return productMapper.select(product);
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
        final String orderItem = request.getOrderItem();
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                        UserUser userUser = new UserUser(useruser);
                        userUser.setOrderItem(orderItem);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (用戶商品報價) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        String permissions = userSelect.get("permissions").toString();
                        List<Map<String, Object>> data = new ArrayList<>();
                        for (Map<String, Object> user : getUserUser) {
                            String order_item = user.get("order_item").toString();
                            String[] order_items = order_item.split(",");
                            for (int i = 0; i < order_items.length; i++) {
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "查詢訂單明細");
                                dataMap.put("username", username);
                                dataMap.put("permissions", permissions);
                                String item = order_items[i];
                                String[] arr = item.split(":");
                                List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                List<String> list = new ArrayList<>();
                                String detail1 = String.format("商品編號:%s", arr[0]);
                                String detail2 = String.format("商品名稱:%s", productsSelect.getFirst().get("products_name").toString());
                                BigDecimal A = new BigDecimal(arr[1]);
                                BigDecimal B = new BigDecimal(productsSelect.getFirst().get("stock").toString());
                                BigDecimal C = B.subtract(A);
                                String detail3 = String.format("%s",
                                        C.compareTo(BigDecimal.ZERO) < 0
                                                ? "庫存不夠" + C.abs() + "筆"
                                                : "庫存足夠");
                                String detail4 = String.format("訂購數量:%s", A);
                                String detail5 = String.format("商品庫存量:%s", B);
                                BigDecimal price = new BigDecimal(productsSelect.getFirst().get("price").toString());
                                String detail6 = String.format("價格:%s", price);
                                Map<String, BigDecimal> queryQuotationsMap = calculateByMargin(price, userPercent);
                                String detail7 = String.format("售價:%s", queryQuotationsMap.get("sellingPrice").toString());
                                String detail8 = String.format("利潤:%s", queryQuotationsMap.get("profit").toString());
                                String detail9 = String.format("利潤率:%s", queryQuotationsMap.get("margin").toString());
                                String detail10 = String.format("描述:%s", productsSelect.getFirst().get("description").toString());
                                list.add(detail1);
                                list.add(detail2);
                                list.add(detail3);
                                list.add(detail4);
                                list.add(detail5);
                                list.add(detail6);
                                list.add(detail7);
                                list.add(detail8);
                                list.add(detail9);
                                list.add(detail10);
                                dataMap.put("user", "訂單帳號 - " + user.get("username").toString());
                                dataMap.put("details" + (i + 1), list);
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
    public ResponseEntity<?> confirmQuotationsProductItem(QuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String orderItem = request.getOrderItem();
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
                        userUser.setOrderItem(orderItem);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (確認報價) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        List<Map<String, Object>> data = new ArrayList<>();

                        UserData userDataDetails = new UserData(useruser);
                        userDataDetails.setOrderItem(orderItem);
                        Map<String, Object> detailsData = getDetailsData(userDataDetails);
                        String order_item = detailsData.get("order_item").toString();
                        String[] order_items = order_item.split(",");
                        Integer quotationsMax = orderbackendMapper.selectQuotationsMax();
                        BigDecimal decimal = new BigDecimal(String.valueOf(quotationsMax + 1));

                        List<Boolean> atLastJudgesList = new ArrayList<>();
                        for (int i = 0; i < order_items.length; i++) {
                            String item = order_items[i];
                            String[] arr = item.split(":");
                            String userUserProductsId = arr[0];
                            List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(userUserProductsId)));
                            BigDecimal stock = new BigDecimal(productsSelect.getFirst().get("stock").toString());
                            boolean judge = stock.subtract(new BigDecimal(arr[1])).compareTo(BigDecimal.ZERO) < 0;
                            atLastJudgesList.add(i, judge);
                        }
                        // 只要其中有一項庫存量不足就不存入DB
                        boolean hasTrue = atLastJudgesList.stream().anyMatch(Boolean.TRUE::equals);
                        if (hasTrue) {
                            Map<String, Object> dataMap = new TreeMap<>();
                            dataMap.put("remark", "確認報價單");
                            dataMap.put("useruser", useruser);
                            dataMap.put("quotationsId", decimal);
                            dataMap.put("stock", "庫存量不夠");
                            data.add(dataMap);
                        } else {
                            try {
                                BigDecimal totalPrice = BigDecimal.ZERO;
                                String state = Backend.STATUS_QUOTATIONS_ESTIMATE.getBackend();
                                for (int i = 0; i < order_items.length; i++) {
                                    Map<String, Object> dataMap = new TreeMap<>();
                                    dataMap.put("remark", "確認完成");
                                    dataMap.put("useruser", useruser);
                                    dataMap.put("quotationsId", decimal);
                                    dataMap.put("stock", "庫存量足夠");
                                    String item = order_items[i];
                                    String[] arr = item.split(":");
                                    String userUserProductsId = arr[0];
                                    String userUserQuantity = arr[1];
                                    BigDecimal quantity = new BigDecimal(userUserQuantity);
                                    List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(userUserProductsId)));
                                    BigDecimal product_id = new BigDecimal(productsSelect.getFirst().get("product_id").toString());
                                    BigDecimal price = new BigDecimal(productsSelect.getFirst().get("price").toString());
                                    Map<String, BigDecimal> queryQuotationsMap = calculateByMargin(price, userPercent);
                                    BigDecimal sellingPrice = new BigDecimal(queryQuotationsMap.get("sellingPrice").toString());
                                    BigDecimal total = sellingPrice.multiply(quantity);
                                    totalPrice = totalPrice.add(total);
                                    List<String> list = new ArrayList<>();
                                    list.add("商品編號: " + product_id);
                                    list.add("商品數量: " + quantity);
                                    list.add("商品單品價格: " + price);
                                    list.add("商品銷售單品價格: " + sellingPrice);
                                    list.add("商品銷售合計: " + total);
                                    dataMap.put("details" + (i + 1), list);
                                    QuotationItems quotationItems = new QuotationItems(decimal, product_id);
                                    quotationItems.setQuantity(quantity);
                                    quotationItems.setPrice(sellingPrice);
                                    quotationItems.setUnit_percent(new BigDecimal(userPercent));
                                    quotationItems.setUnit_price(price);
                                    orderbackendMapper.createQuotationItems(quotationItems);
                                    dataMap.put("state", StatusKey.quotationsStatusKey.get(state));
                                    data.add(dataMap);
                                }
                                Quotations quotations = new Quotations(decimal);
                                quotations.setUsername(useruser);
                                quotations.setStatus(state);
                                quotations.setTotal_price(totalPrice);
                                orderbackendMapper.createQuotations(quotations);

                                NotificationMessage notificationMessage = new NotificationMessage(useruser);
                                notificationMessage.setTitle("報價建立");
                                notificationMessage.setContent("報價單建立成功");
                                notificationMessage.setType("quotation");
                                rabbitService.quotationCreate(notificationMessage);
                            } catch (DataIntegrityViolationException e) {
                                logger.warn("confirmQuotationsProductItem 新增報價資料不合法，username={}", username);
                                throw new IsViolationException(username + " - 新增報價資料不合法", e);
                            } catch (DataAccessException e) {
                                logger.error("confirmQuotationsProductItem 資料庫錯誤，username={}", username);
                                throw new DBException(username + " - 系統錯誤，請稍後再試", e);
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
    public ResponseEntity<?> deleteQuotationsProduct(DeleteQuotationsProductItemRequest request) {
        final String username = request.getUsername();
        final String token = request.getToken();
        final String useruser = request.getUseruser();
        final String orderItem = request.getOrderItem();
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
                        userUser.setOrderItem(orderItem);
                        List<Map<String, Object>> getUserUser = orderbackendMapper.selectUserUser(userUser);
                        if (getUserUser.isEmpty()) {
                            logger.error("{} : (刪除報價) 用戶不存在", useruser);
                            throw new ResourceNotFoundException(useruser + " - 用戶不存在");
                        }
                        UserData userDataDetails = new UserData(useruser);
                        userDataDetails.setOrderItem(orderItem);
                        List<Map<String, Object>> quotationsData = getQuotationsData(userDataDetails);
                        List<Map<String, Object>> data = new ArrayList<>();
                        for (int i = 0; i < quotationsData.size(); i++) {
                            Map<String, Object> dataMap = new TreeMap<>();
                            dataMap.put("remark", "刪除完成");
                            dataMap.put("useruser", useruser);
                            Map<String, Object> quotationData = quotationsData.get(i);
                            BigDecimal quotation_idDel = new BigDecimal(quotationData.get("quotation_id").toString());
                            String usernameDel = quotationData.get("username").toString();
                            String statusDel = quotationData.get("status").toString();
                            StringBuilder msg = new StringBuilder();
                            if (Backend.STATUS_QUOTATIONS_ESTIMATE.getBackend().equals(statusDel)) {
                                QuotationItems quotationItems = new QuotationItems(quotation_idDel, null);
                                orderbackendMapper.delQuotationItems(quotationItems);
                                Quotations quotations = new Quotations(quotation_idDel);
                                quotations.setUsername(usernameDel);
                                orderbackendMapper.delQuotations(quotations);
                                msg.append("報價單刪除成功");
                            } else {
                                msg.append("報價已送出無法刪除");
                            }
                            dataMap.put("quotationsId", quotation_idDel);
                            List<String> list = new ArrayList<>();
                            list.add(msg.toString());
                            dataMap.put("details" + (i + 1), list);
                            dataMap.put("state", StatusKey.quotationsStatusKey.get(statusDel));
                            data.add(dataMap);
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
                        UserData userDataDetails = new UserData(useruser);
                        List<Map<String, Object>> quotationsData = orderbackendMapper.quotationsItemsProductsData(userDataDetails);
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
                        List<Map<String, Object>> data = new ArrayList<>();
                        for (Map.Entry<String, List<Map<String, Object>>> entry : sorte.entrySet()) {
                            String key = entry.getKey();
                            List<Map<String, Object>> lists = entry.getValue();
                            for (int i = 0; i < lists.size(); i++) {
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "查詢報價單");
                                dataMap.put("useruser", useruser);
                                dataMap.put("quotationsId", key);
                                dataMap.put("stock", "庫存量足夠");
                                Map<String, Object> quotationData = lists.get(i);
                                String statusQuery = quotationData.get("status").toString();
                                BigDecimal quantityQuery = new BigDecimal(quotationData.get("quantity").toString());
                                BigDecimal priceQuery = new BigDecimal(quotationData.get("price").toString());
                                BigDecimal sumPriceQuery = new BigDecimal(quotationData.get("sum_price").toString());
                                String productsNameQuery = quotationData.get("products_name").toString();
                                String descriptionQuery = quotationData.get("description").toString();
                                // estimate（預估） / sent（已送出） / accepted（客戶接受） / rejected（拒絕）
                                List<String> list = new ArrayList<>();
                                list.add("數量: " + quantityQuery);
                                list.add("價格: " + priceQuery);
                                list.add("合計: " + sumPriceQuery);
                                list.add("名稱: " + productsNameQuery);
                                list.add("描述: " + descriptionQuery);
                                dataMap.put("details" + (i + 1), list);
                                dataMap.put("state", StatusKey.quotationsStatusKey.get(statusQuery));
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
                        UserDataSend userDataDetails = new UserDataSend(useruser);
                        String send = Backend.STATUS_QUOTATIONS_SENT.getBackend();
                        userDataDetails.setStatus(send);
                        userDataDetails.setQuotation_id(new BigDecimal(userUserQuotationsId));
                        Map<String, Object> quotationsDataSend = getQuotationsDataSend(userDataDetails);
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("remark", "送出報價單");
                        dataMap.put("useruser", useruser);
                        dataMap.put("quotationsId", userUserQuotationsId);
                        dataMap.put("stock", "庫存量足夠");
                        List<String> list = new ArrayList<>();
                        if (quotationsDataSend == null) {
                            list.add("送出失敗，無此報價單");
                            dataMap.put("details" + (1), list);
                        } else {
                            String statusSend = quotationsDataSend.get("status").toString();
                            if (Backend.STATUS_QUOTATIONS_ESTIMATE.getBackend().equals(statusSend)) {
                                orderbackendMapper.updateQuotations(userDataDetails);
                                orderbackendMapper.updateUserdataDetailIsActiveNotifications(userDataDetails);
                                list.add("送出成功");
                                dataMap.put("details" + (1), list);
                                dataMap.put("state", StatusKey.quotationsStatusKey.get(send));
                            } else {
                                list.add("已送出，勿重複送單");
                                dataMap.put("details" + (1), list);
                            }
                        }
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
