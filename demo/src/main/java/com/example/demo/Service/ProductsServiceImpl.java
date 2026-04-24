package com.example.demo.Service;

import com.example.demo.Common.ConvertFormat;
import com.example.demo.Dto.ApiResponse;
import com.example.demo.Dto.Products.Product;
import com.example.demo.Dto.Products.ProductsRequest;
import com.example.demo.Dto.Products.QueryProductsRequest;
import com.example.demo.Dto.Products.UpdateProductsRequest;
import com.example.demo.Exception.*;
import com.example.demo.Mapper.ProductMapper;
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
import java.util.*;
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
				List<Map<String, Object>> data = List.of(Map.of("ex", "新增，資源忙碌，請重試"));
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
                    List<Map<String, Object>> productsSelect = getProduct(product);
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
                List<Map<String, Object>> data = List.of(Map.of("ex", "查詢，資源忙碌，請重試"));
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
                    int cnt = productMapper.update(product);
                    if (cnt == 0) {
                        throw new ResourceNotFoundException(productId + " - 更改商品不存在");
                    }
                    List<Map<String, Object>> productsSelect = getProduct(product);
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
                List<Map<String, Object>> data = List.of(Map.of("ex", "刪除，資源忙碌，請重試"));
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
