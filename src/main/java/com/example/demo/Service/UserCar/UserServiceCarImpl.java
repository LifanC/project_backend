package com.example.demo.Service.UserCar;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Common.*;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.User.*;
import com.example.demo.Exception.BadRequestException;
import com.example.demo.Exception.DBException;
import com.example.demo.Exception.IsViolationException;
import com.example.demo.Exception.ResourceNotFoundException;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class UserServiceCarImpl implements UserServiceCar {

    private final Logger logger = LoggerFactory.getLogger(UserServiceCarImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final SecretMapper secretMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public UserServiceCarImpl(
            SecretMapper secretMapper,
            UserMapper userMapper,
            ProductMapper productMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.secretMapper = secretMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
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
        final String[] product_ids = request.getProduct_ids();
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                            BigDecimal product_idNum = new BigDecimal(product_id);
                            product.setProduct_id(product_idNum);

                            BigDecimal quantity = null;
                            if (product_ids != null && product_ids.length == 2 &&
                                    StringUtils.hasText(product_ids[0]) &&
                                    StringUtils.hasText(product_ids[1])) {

                                BigDecimal A = new BigDecimal(product_ids[0]);
                                BigDecimal B = new BigDecimal(product_ids[1]);

                                if (A.compareTo(B) < 0) {
                                    quantity = B.subtract(A);
                                }
                            }
                            product.setProduct_id_quantity(quantity);
                        }

                        List<Map<String, Object>> productsCarSelect = getProduct(product);
                        if (productsCarSelect.isEmpty()) {
                            throw new ResourceNotFoundException("商品不存在");
                        }
                        logger.info("User 商品查詢成功");
                        List<Map<String, Object>> data = new ArrayList<>();
                        for (Map<String, Object> map : productsCarSelect) {
                            Map<String, Object> dataMap = new TreeMap<>();
                            dataMap.put("remark", "商品查詢成功");
                            dataMap.put("product_id", map.get("product_id").toString());
                            dataMap.put("products_name", map.get("products_name").toString());
                            dataMap.put("description", map.get("description").toString());
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                            List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(product_id)));
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (productsSelect.isEmpty()) {
                                throw new ResourceNotFoundException(product_id + " - 商品不存在");
                            } else {
                                if (userdataDetailsSelect == null) {
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

                                        String result = list.stream()
                                                .map(String::valueOf)
                                                .collect(Collectors.joining(","));
                                        userdataDetails.setOrder_item_str(result);
                                    } else {
                                        userdataDetails.setOrder_item_str(product_id + ":" + product_quantity);
                                    }
                                    userMapper.updateUserdataDetail(userdataDetails);
                                }
                                userdataDetailsSelect = getUserDataDetail(userdataDetails);
                                List<Map<String, Object>> data = new ArrayList<>();
                                String orderItem = userdataDetailsSelect.get("order_item").toString();
                                for (String item : orderItem.split(",")) {
                                    String[] arr = item.split(":");
                                    productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                    Map<String, Object> dataMap = new TreeMap<>();
                                    dataMap.put("remark", "新增商品成功");
                                    dataMap.put("username", userdataDetailsSelect.get("username"));
                                    dataMap.put("product_id", productsSelect.getFirst().get("product_id").toString());
                                    dataMap.put("products_name", productsSelect.getFirst().get("products_name").toString());
                                    dataMap.put("product_quantity", new BigDecimal(arr[1]));
                                    dataMap.put("description", productsSelect.getFirst().get("description").toString());
                                    String created_date = userdataDetailsSelect.get("created_date").toString();
                                    String updated_date = userdataDetailsSelect.get("updated_date").toString();
                                    dataMap.put("created_date", ConvertFormat.time(created_date));
                                    dataMap.put("updated_date", ConvertFormat.time(updated_date));
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
                        "帳號-" + username + " - 新增購物車，資源忙碌，請重試"
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                        UserdataDetails userdataDetails = new UserdataDetails(username);
                        Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                        if (userdataDetailsSelect == null) {
                            logger.error("{} : (查詢購物車) 訂單不存在", username);
                            throw new ResourceNotFoundException(username + " - 訂單不存在");
                        }
                        List<Map<String, Object>> data = new ArrayList<>();
                        String orderItem = userdataDetailsSelect.get("order_item").toString();
                        Map<String, Object> dataMap = new TreeMap<>();
                        String remark = "新增商品至購物車";
                        if (StringUtils.hasText(orderItem)) {
                            remark = "查詢購物車成功";
                            String[] list = orderItem.split(",");
                            for (int i = 0; i < list.length; i++) {
                                String[] arr = list[i].split(":");
                                List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                String productId = productsSelect.getFirst().get("product_id").toString();
                                String productsName = productsSelect.getFirst().get("products_name").toString();
                                BigDecimal productQuantity = new BigDecimal(arr[1]);
                                String description = productsSelect.getFirst().get("description").toString();
                                StringBuilder details = new StringBuilder();
                                details.append(String.format(
                                        "商品編號(%s)名稱(%s)數量(%s)描述(%s)",
                                        productId, productsName, productQuantity, description
                                ));
                                dataMap.put("details" + (i + 1), details);
                            }
                        }
                        dataMap.put("remark", remark);
                        dataMap.put("username", username);
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
                        "帳號-" + username + " - 查詢購物車，資源忙碌，請重試"
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                            List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(product_id)));
                            Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                            if (productsSelect.isEmpty()) {
                                throw new ResourceNotFoundException(product_id + " - 商品不存在");
                            } else {
                                if (userdataDetailsSelect == null) {
                                    logger.error("{} : (更改購物車) 訂單不存在", username);
                                    throw new ResourceNotFoundException(username + " - 訂單不存在");
                                }
                                String orderItem = userdataDetailsSelect.get("order_item").toString();
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

                                    String result = list.stream()
                                            .map(String::valueOf)
                                            .collect(Collectors.joining(","));
                                    userdataDetails.setOrder_item_str(result);
                                    userMapper.updateUserdataDetail(userdataDetails);
                                    userdataDetailsSelect = getUserDataDetail(userdataDetails);
                                }
                                List<Map<String, Object>> data = new ArrayList<>();
                                orderItem = userdataDetailsSelect.get("order_item").toString();
                                for (String item : orderItem.split(",")) {
                                    String[] arr = item.split(":");
                                    productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                    Map<String, Object> dataMap = new TreeMap<>();
                                    dataMap.put("remark", "更改商品成功");
                                    dataMap.put("username", userdataDetailsSelect.get("username"));
                                    dataMap.put("product_id", productsSelect.getFirst().get("product_id").toString());
                                    dataMap.put("products_name", productsSelect.getFirst().get("products_name").toString());
                                    dataMap.put("product_quantity", new BigDecimal(arr[1]));
                                    dataMap.put("description", productsSelect.getFirst().get("description").toString());
                                    String created_date = userdataDetailsSelect.get("created_date").toString();
                                    String updated_date = userdataDetailsSelect.get("updated_date").toString();
                                    dataMap.put("created_date", ConvertFormat.time(created_date));
                                    dataMap.put("updated_date", ConvertFormat.time(updated_date));
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
                        "帳號-" + username + " - 更改購物車，資源忙碌，請重試"
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                            List<Map<String, Object>> data = new ArrayList<>();
                            if (!tempList.isEmpty()) {
                                Map<String, Object> dataMap = new TreeMap<>();
                                dataMap.put("remark", "刪除商品成功");
                                dataMap.put("username",  username);
                                List<Map<String, Object>> productsSelect;
                                int cnt = 0;
                                for (String hasNotSame : hasNotSameList) {
                                    productsSelect = getProduct(new Product(new BigDecimal(hasNotSame)));
                                    String hasNotProduct_id = productsSelect.getFirst().get("product_id").toString();
                                    String hasNotProduct_name = productsSelect.getFirst().get("products_name").toString();
                                    StringBuilder details = new StringBuilder();
                                    details.append(String.format(
                                            "商品編號(%s) 商品名稱(%s)",
                                            hasNotProduct_id, hasNotProduct_name
                                    ));
                                    cnt++;
                                    dataMap.put("details" + cnt, details);
                                }
                                for (String temp : tempList) {
                                    productsSelect = getProduct(new Product(new BigDecimal(temp)));
                                    String delProduct_id = productsSelect.getFirst().get("product_id").toString();
                                    String delProduct_name = productsSelect.getFirst().get("products_name").toString();
                                    StringBuilder details = new StringBuilder();
                                    details.append(String.format(
                                            "刪除 - 商品編號(%s) 商品名稱(%s)",
                                            delProduct_id, delProduct_name
                                    ));
                                    cnt++;
                                    dataMap.put("details" + cnt, details);
                                }
                                data.add(dataMap);
                                String result = hasNotSameListUpdateDb.stream()
                                        .map(String::valueOf)
                                        .collect(Collectors.joining(","));
                                userdataDetails.setOrder_item_str(result);
                                userMapper.updateUserdataDetail(userdataDetails);
                            } else {
                                throw new ResourceNotFoundException(username + " - 無此商品");
                            }
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
                        "帳號-" + username + " - 購物車刪除，資源忙碌，請重試"
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
                            userSelect = objectMapper.readValue(json, new TypeReference<>() {});
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
                        UserdataDetails userdataDetails = new UserdataDetails(username);
                        Map<String, Object> userdataDetailsSelect = getUserDataDetail(userdataDetails);
                        if (userdataDetailsSelect == null) {
                            logger.error("{} : (確認訂單) 訂單不存在", username);
                            throw new ResourceNotFoundException(username + " - 訂單不存在");
                        }
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        String orderItem = userdataDetailsSelect.get("order_item").toString();
                        logger.info("(確認訂單){}", orderItem);
                        String remark = "新增商品至購物車";
                        if (StringUtils.hasText(orderItem)) {
                            String[] list = orderItem.split(",");
                            List<Boolean> negativeNumbers = new ArrayList<>();
                            for (int i = 0; i < list.length; i++) {
                                String[] arr = list[i].split(":");
                                List<Map<String, Object>> productsSelect = getProduct(new Product(new BigDecimal(arr[0])));
                                String productId = productsSelect.getFirst().get("product_id").toString();
                                String productsName = productsSelect.getFirst().get("products_name").toString();
                                BigDecimal stock = new BigDecimal(productsSelect.getFirst().get("stock").toString());
                                BigDecimal productQuantity = new BigDecimal(arr[1]);
                                String description = productsSelect.getFirst().get("description").toString();
                                BigDecimal negative = stock.subtract(productQuantity);
                                negativeNumbers.add(negative.compareTo(BigDecimal.ZERO) < 0);
                                StringBuilder details = new StringBuilder();
                                details.append(String.format(
                                        "商品編號(%s)名稱(%s)下單數量(%s)商品庫存量(%s)(%s)",
                                        productId, productsName, productQuantity, stock, description
                                ));
                                dataMap.put("details" + (i + 1), details);
                            }
                            if (negativeNumbers.stream().anyMatch(Boolean.TRUE::equals)) {
                                remark = "商品下單失敗-庫存量不夠";
                            } else {
                                userMapper.updateUserdataDetailIsActive(username);
                                remark = "商品下單成功";
                            }
                        }
                        dataMap.put("remark", remark);
                        dataMap.put("username", username);
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
                        "帳號-" + username + " - 確認訂單，資源忙碌，請重試"
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
