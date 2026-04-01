package com.example.demo.Service;

import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.Products.ProductsRequest;
import com.example.demo.Dto.Products.QueryProductsRequest;
import com.example.demo.Dto.Products.UpdateProductsRequest;
import com.example.demo.Exception.DBException;
import com.example.demo.Exception.IsViolationException;
import com.example.demo.Exception.ResourceAlreadyExistsException;
import com.example.demo.Exception.ResourceNotFoundException;
import com.example.demo.Mapper.ProductMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Service
public class ProductsServiceImpl implements ProductsService {

    private final Logger logger = LoggerFactory.getLogger(ProductsServiceImpl.class);

    private final ProductMapper productMapper;

    public ProductsServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    // 單次回源鎖
    private final ReentrantLock lock = new ReentrantLock();

    private List<Map<String, Object>> getProduct(Product product) {
        return productMapper.select(product);
    }

    @Override
    public ResponseEntity<?> testLogin() {
        logger.info("products/testLogin: Products is working!");
        List<Object> messageList = List.of("Products is working!");
        Map<String, List<Object>> message = Map.of("content", messageList);
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
                        List<Object> messageList = new ArrayList<>();
                        messageList.add("---------------------------------------");
                        messageList.add(products_name + " - 新增商品成功");
                        messageList.add("商品編號 - " + productsSelect.getFirst().get("product_id").toString());
                        messageList.add("商品名稱 - " + products_name);
                        messageList.add("價格 - " + price);
                        messageList.add("庫存量 - " + stock);
                        messageList.add("描述 - " + description);
                        messageList.add("新增日期" + ((Timestamp) productsSelect.getFirst().get("created_date")).toLocalDateTime());
                        messageList.add("更改日期" + ((Timestamp) productsSelect.getFirst().get("updated_date")).toLocalDateTime());
                        messageList.add("---------------------------------------");
                        Map<String, List<Object>> message = Map.of("content", messageList);
                        HttpStatus status = HttpStatus.CREATED;
                        return ResponseEntity
                                .status(status)
                                .body(ApiResponse.api(
                                        status,
                                        message
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
                List<Object> messageList = List.of(
                        "商品名稱 -" + products_name,
                        products_name + " - 新增，資源忙碌，請重試"
                );
                Map<String, List<Object>> message = Map.of("content", messageList);
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
    public ResponseEntity<?> select(QueryProductsRequest request) {
        Product product = new Product();
        final String productId = request.getProduct_id().trim();
        if (StringUtils.hasText(productId)) {
            product.setProduct_id(new BigDecimal(productId));
        }
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Products select 拿鎖");
                try {
                    List<Map<String, Object>> productsSelect = getProduct(product);
                    if (productsSelect.isEmpty()) {
                        throw new ResourceNotFoundException("查詢商品不存在");
                    }
                    logger.info("Products 商品查詢成功");
                    List<Object> messageList = new ArrayList<>();
                    for (Map<String, Object> map : productsSelect) {
                        String products_name = map.get("products_name").toString();
                        BigDecimal price = new BigDecimal(map.get("price").toString());
                        BigDecimal stock = new BigDecimal(map.get("stock").toString());
                        String description = map.get("description").toString();
                        messageList.add("---------------------------------------");
                        messageList.add("商品編號 - " + productId);
                        messageList.add("商品名稱 - " + products_name);
                        messageList.add("價格 - " + price);
                        messageList.add("庫存量 - " + stock);
                        messageList.add("描述 - " + description);
                        messageList.add("新增日期" + ((Timestamp) map.get("created_date")).toLocalDateTime());
                        messageList.add("更改日期" + ((Timestamp) map.get("updated_date")).toLocalDateTime());
                        messageList.add("---------------------------------------");
                    }
                    Map<String, List<Object>> message = Map.of("content", messageList);
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
                logger.error("select 資源忙碌，請重試");
                Map<String, List<Object>> message = Map.of(
                        "content", List.of(
                                "查詢，資源忙碌，請重試"
                        )
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
                    int cnt = productMapper.update(product);
                    if (cnt == 0) {
                        throw new ResourceNotFoundException(productId + " - 更改商品不存在");
                    }
                    logger.info("Products 商品更改成功");
                    List<Map<String, Object>> productsSelect = getProduct(product);
                    List<Object> messageList = List.of(
                            "---------------------------------------",
                            "商品編號 - " + productId,
                            "商品名稱 - " + productsSelect.getFirst().get("products_name"),
                            "價格 - " + productsSelect.getFirst().get("price"),
                            "庫存量 - " + productsSelect.getFirst().get("stock"),
                            "描述 - " + productsSelect.getFirst().get("description"),
                            "更改日期" + ((Timestamp) productsSelect.getFirst().get("updated_date")).toLocalDateTime(),
                            "---------------------------------------");
                    Map<String, List<Object>> message = Map.of("content", messageList);
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
                logger.error("update 資源忙碌，請重試");
                Map<String, List<Object>> message = Map.of(
                        "content", List.of(
                                "更改，資源忙碌，請重試"
                        )
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
    public ResponseEntity<?> delete(String productId) {
        Product product = new Product();
        product.setProduct_id(new BigDecimal(productId.trim()));
        try {
            // 嘗試拿鎖，確保同一時間只有一個線程回源。只會用於SELECT
            if (lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                logger.info("Products delete 拿鎖");
                try {
                    int cnt = productMapper.delete(product);
                    if (cnt == 0) {
                        throw new ResourceNotFoundException(productId + " - 刪除商品不存在");
                    }
                    List<Object> messageList = List.of(
                            "---------------------------------------",
                            "商品編號 - " + productId,
                            "刪除日期" + LocalDateTime.now(),
                            "---------------------------------------");
                    logger.info("Products 商品刪除成功");
                    Map<String, List<Object>> message = Map.of("content", messageList);
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
                logger.error("delete 資源忙碌，請重試");
                Map<String, List<Object>> message = Map.of(
                        "content", List.of(
                                "刪除，資源忙碌，請重試"
                        )
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
