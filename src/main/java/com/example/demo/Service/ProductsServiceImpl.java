package com.example.demo.Service;

import com.example.demo.Common.ConvertFormat;
import com.example.demo.Common.RedisKey;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Products.*;
import com.example.demo.Exception.*;
import com.example.demo.Mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Service
public class ProductsServiceImpl implements ProductsService {

    private final Logger logger = LoggerFactory.getLogger(ProductsServiceImpl.class);

    // ? redis 到期時間 Seconds
    @Value("${jwt.expirationSeconds}")
    private long expirationSeconds;

    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ProductsServiceImpl(
            ProductMapper productMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.productMapper = productMapper;
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

    private List<Map<String, Object>> getProduct(Product product) {
        return productMapper.select(product);
    }

    @Override
    public ResponseEntity<?> testLogin() {
        logger.info("products/testLogin: Products is working!");
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("testLogin 拿鎖");
                try {
                    List<Map<String, Object>> data = new ArrayList<>();
                    Map<String, Object> dataMap = new TreeMap<>();
                    dataMap.put("status_name", "狀態");
                    dataMap.put("status", "Products is working!");
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

    private List<Map<String, Object>> getRedisMethodList(String key) {
        List<Map<String, Object>> list = new ArrayList<>();
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            list = objectMapper.readValue(json, new TypeReference<>() {});
        }
        return list;
    }

    @Override
    @Transactional
    public ResponseEntity<?> insert(ProductsRequest request) {
        final String products_name = request.getProducts_name().trim();
        final BigDecimal price = new BigDecimal(request.getPrice().trim());
        final BigDecimal stock = new BigDecimal(request.getStock().trim());
        final String description = request.getDescription().trim();
        Product product = new Product(products_name, price, stock, description);
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Products insert 拿鎖");
                try {
                    try {
                        productMapper.create(product);
                        List<Map<String, Object>> productsSelect = getProduct(product);
                        List<Map<String, Object>> data = new ArrayList<>();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("remark", "新增商品成功");
                        dataMap.put("product_id", productsSelect.getFirst().get("product_id").toString());
                        dataMap.put("products_name", products_name);
                        dataMap.put("price", price);
                        dataMap.put("stock", stock);
                        dataMap.put("description", description);
                        dataMap.put("created_date", ConvertFormat.time(productsSelect.getFirst().get("created_date").toString()));
                        dataMap.put("updated_date", ConvertFormat.time(productsSelect.getFirst().get("updated_date").toString()));
                        data.add(dataMap);
                        HttpStatus status = HttpStatus.OK;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        data
                                ));
                    } catch (DuplicateKeyException e) {
                        logger.warn("Products 新增失敗，新增商品已存在，products_name={}", products_name);
                        throw new ResourceAlreadyExistsException(products_name + " - 新增商品已存在", e);
                    } catch (DataIntegrityViolationException e) {
                        logger.warn("Products 新增失敗，新增商品資料不合法，products_name={}", products_name);
                        throw new IsViolationException(products_name + " - 新增商品資料不合法", e);
                    } catch (DataAccessException e) {
                        logger.error("Products 資料庫錯誤，products_name={}", products_name);
                        throw new DBException(products_name + " - 系統錯誤，請稍後再試", e);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("{} : insert 資源忙碌，請重試", products_name);
                List<Map<String, Object>> data = List.of(Map.of("remark", "新增，資源忙碌，請重試"));
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
    public ResponseEntity<?> select(QueryProductsRequest request) {
        Product product = new Product();
        final String productId = request.getProduct_id();
        if (StringUtils.hasText(productId)) {
            boolean isNumber = productId.matches("^\\d+$");
            if (!isNumber) {
                logger.error("{} - 商品編號只能包含數字", productId);
                throw new BadRequestException("商品編號只能包含數字 - " + productId);
            }
            product.setProduct_id(new BigDecimal(productId));
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Products select 拿鎖");
                try {

                    String key = RedisKey.redisProductsKey.get("productsAll").replace("{1}", "*");
                    if (StringUtils.hasText(productId)) {
                        key = RedisKey.redisProductsKey.get("productsOnly").replace("{1}", productId);
                    }
                    List<Map<String, Object>> productsSelect = getRedisMethodList(key);
                    if (productsSelect.isEmpty()) {
                        productsSelect = getProduct(product);
                        String jsonMap = objectMapper.writeValueAsString(productsSelect);
                        stringRedisTemplate.opsForValue().set(
                                key, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }

                    if (productsSelect.isEmpty()) {
                        throw new ResourceNotFoundException("查詢商品不存在");
                    }

                    logger.info("Products 商品查詢成功");
                    List<Map<String, Object>> data = new ArrayList<>();
                    for (Map<String, Object> map : productsSelect) {
                        String product_id = map.get("product_id").toString();
                        String products_name = map.get("products_name").toString();
                        BigDecimal price = new BigDecimal(map.get("price").toString());
                        BigDecimal stock = new BigDecimal(map.get("stock").toString());
                        String description = map.get("description").toString();
                        Map<String, Object> dataMap = new TreeMap<>();
                        dataMap.put("product_id", product_id);
                        dataMap.put("products_name", products_name);
                        dataMap.put("price", price);
                        dataMap.put("stock", stock);
                        dataMap.put("description", description);
                        dataMap.put("created_date", ConvertFormat.time(productsSelect.getFirst().get("created_date").toString()));
                        dataMap.put("updated_date", ConvertFormat.time(productsSelect.getFirst().get("updated_date").toString()));
                        data.add(dataMap);
                    }
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
                logger.error("select 資源忙碌，請重試");
                List<Map<String, Object>> data = List.of(Map.of("remark", "查詢，資源忙碌，請重試"));
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
    public ResponseEntity<?> update(UpdateProductsRequest request) {
        if (Stream.of(
                request.getProducts_name().trim(),
                request.getPrice().trim(),
                request.getStock().trim(),
                request.getDescription().trim()
        ).noneMatch(StringUtils::hasText)) {
            throw new ResourceNotFoundException("更改商品欄位至少輸入一項");
        }
        final String products_name = request.getProducts_name().trim();
        final String description = request.getDescription().trim();
        final BigDecimal price = StringUtils.hasText(request.getPrice())
                ? new BigDecimal(request.getPrice().trim())
                : null;
        final BigDecimal stock = StringUtils.hasText(request.getStock())
                ? new BigDecimal(request.getStock().trim())
                : null;
        final String productId = request.getProduct_id().trim();
        Product product = new Product(products_name, price, stock, description);
        product.setProduct_id(new BigDecimal(productId));
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Products update 拿鎖");
                try {

                    String productsOnly = RedisKey.redisProductsKey.get("productsOnly").replace("{1}", productId);
                    List<Map<String, Object>> productsSelect = getRedisMethodList(productsOnly);
                    if (productsSelect.isEmpty()) {
                        productsSelect = getProduct(product);
                        String jsonMap = objectMapper.writeValueAsString(productsSelect);
                        stringRedisTemplate.opsForValue().set(
                                productsOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }

                    if (productsSelect.isEmpty()) {
                        throw new ResourceNotFoundException("更改商品不存在");
                    }

                    productMapper.update(product);
                    productsSelect = getProduct(product);
                    String jsonMap = objectMapper.writeValueAsString(productsSelect);
                    stringRedisTemplate.opsForValue().set(
                            productsOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    List<Map<String, Object>> data = new ArrayList<>();
                    Map<String, Object> dataMap = new TreeMap<>();
                    dataMap.put("remark", "商品更改成功");
                    dataMap.put("product_id", productsSelect.getFirst().get("product_id").toString());
                    dataMap.put("products_name", productsSelect.getFirst().get("products_name").toString());
                    dataMap.put("price", productsSelect.getFirst().get("price").toString());
                    dataMap.put("stock", productsSelect.getFirst().get("stock").toString());
                    dataMap.put("description", productsSelect.getFirst().get("description").toString());
                    dataMap.put("created_date", ConvertFormat.time(productsSelect.getFirst().get("created_date").toString()));
                    dataMap.put("updated_date", ConvertFormat.time(productsSelect.getFirst().get("updated_date").toString()));
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
                logger.error("update 資源忙碌，請重試");
                List<Object> messageList = List.of(
                        "商品名稱 -" + products_name,
                        products_name + " - 更改，資源忙碌，請重試"
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
    public ResponseEntity<?> delete(DeleteProductsRequest request) {
        String productId = request.getProduct_id().trim();
        Product product = new Product();
        product.setProduct_id(new BigDecimal(productId));
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Products delete 拿鎖");
                try {

                    String productsOnly = RedisKey.redisProductsKey.get("productsOnly").replace("{1}", productId);
                    List<Map<String, Object>> productsSelect = getRedisMethodList(productsOnly);
                    if (productsSelect.isEmpty()) {
                        productsSelect = getProduct(product);
                        String jsonMap = objectMapper.writeValueAsString(productsSelect);
                        stringRedisTemplate.opsForValue().set(
                                productsOnly, jsonMap, expirationSecondsAddRndomNumber(), TimeUnit.SECONDS);
                    }

                    if (productsSelect.isEmpty()) {
                        throw new ResourceNotFoundException("刪除商品不存在");
                    }

                    productMapper.delete(product);
                    stringRedisTemplate.delete(productsOnly);
                    List<Map<String, Object>> data = new ArrayList<>();
                    Map<String, Object> dataMap = new TreeMap<>();
                    dataMap.put("remark", "商品刪除成功");
                    dataMap.put("product_id", productId);
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
                logger.error("delete 資源忙碌，請重試");
                List<Map<String, Object>> data = List.of(Map.of("remark", "刪除，資源忙碌，請重試"));
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
    public ResponseEntity<?> uploadFile(MultipartFile request) {
        try {
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Products uploadFile 拿鎖");
                try {
                    if (request.isEmpty()) {
                        throw new ResourceNotFoundException("未選擇檔案");
                    }

                    String originaFileName = request.getOriginalFilename();
                    if (originaFileName == null || !originaFileName.toLowerCase().endsWith(".csv")) {
                        throw new BadRequestException("無效的.csv");
                    }

                    List<Map<String, Object>> data = new ArrayList<>();

                    long size = request.getSize();
                    String type = request.getContentType();
                    logger.info("originaFileName: {}", originaFileName);
                    logger.info("size: {}", size);
                    logger.info("type: {}", type);

                    byte[] bytes = request.getBytes();

                    // 1️⃣ 檢查 BOM（放最前面）
                    boolean hasBom =
                            bytes.length >= 3 &&
                                    (bytes[0] & 0xFF) == 0xEF &&
                                    (bytes[1] & 0xFF) == 0xBB &&
                                    (bytes[2] & 0xFF) == 0xBF;

                    boolean isUtf8 = true;
                    try {
                        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                        decoder.onMalformedInput(CodingErrorAction.REPORT);
                        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                        decoder.decode(ByteBuffer.wrap(bytes));
                    } catch (Exception e) {
                        isUtf8 = false;
                    }

                    // 2️⃣ UTF-8 驗證（包含 BOM 或純 UTF-8）
                    if (!hasBom && !isUtf8) {
                        throw new BadRequestException("只允許 UTF-8 CSV");
                    }

                    // 3️⃣ 如果有 BOM，要跳過 BOM 再讀
                    int offset = hasBom ? 3 : 0;

                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                                    new ByteArrayInputStream(bytes, offset, bytes.length - offset),
                                    StandardCharsets.UTF_8
                            ))) {
                        String line;

                        int cnt = 0;
                        final int batchSize = 1000;
                        List<Product> products = new ArrayList<>();
                        int correct = 0;

                        List<Map<String, Object>> data1 = new ArrayList<>();
                        List<Map<String, Object>> data2 = new ArrayList<>();
                        while ((line = br.readLine()) != null) {
                            cnt++;
                            if (cnt == 1) continue; // 表頭
                            String[] split = line.split(",");
                            Boolean[] checks = new Boolean[split.length];
                            checks[0] = true;
                            checks[1] = split[1].matches("^\\d+$");
                            checks[2] = split[2].matches("^\\d+$");
                            checks[3] = true;
                            logger.debug("第{}筆: {}", (cnt - 1), line);
                            if (Arrays.stream(checks).allMatch(Boolean.TRUE::equals)) {
                                correct++;
                                Product product = new Product();
                                product.setProducts_name(split[0]);
                                product.setPrice(new BigDecimal(split[1]));
                                product.setStock(new BigDecimal(split[2]));
                                product.setDescription(split[3]);
                                products.add(product);
                                if (products.size() == batchSize) {
                                    productMapper.batchUpsert(products);
                                    products.clear();
                                }
                            } else {
                                Map<String, Object> dataMap2 = new TreeMap<>();
                                dataMap2.put("remark", "上傳");
                                dataMap2.put("directions", "第" + (cnt - 1) + "筆");
                                dataMap2.put("details" + (cnt - 1), List.of(
                                        split[0],
                                        checks[1] ? "金額" : "金額錯誤",
                                        checks[2] ? "庫存量" : "庫存量錯誤",
                                        split[3]
                                ));
                                dataMap2.put("state", "上傳失敗");
                                data2.add(dataMap2);
                            }
                        }
                        if (!products.isEmpty()) {
                            productMapper.batchUpsert(products);
                        }

                        Map<String, Object> dataMap1 = new TreeMap<>();
                        dataMap1.put("remark", "上傳");
                        dataMap1.put("directions", "正確新增筆數: " + correct + "筆");
                        dataMap1.put("state", "上傳成功");
                        data1.add(dataMap1);

                        data.addAll(data1);
                        data.addAll(data2);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    HttpStatus status = HttpStatus.OK;
                    return ResponseEntity
                            .status(status)
                            .body(ApiResponse.api(
                                    status,
                                    data
                            ));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                }
            } else {
                // 沒拿到鎖的線程稍等一下再從快取讀
                Thread.sleep(20);
                logger.error("uploadFile 資源忙碌，請重試");
                List<Map<String, Object>> data = List.of(Map.of("remark", "上傳，資源忙碌，請重試"));
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
