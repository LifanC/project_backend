package com.example.demo.Controller;

import com.example.demo.Dto.Products.*;
import com.example.demo.Service.ProductsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Product API", description = "商品相關功能")
@RestController
@RequestMapping("/v1/products")
@Validated
public class ProductsController {

    private final ProductsService productsService;

    public ProductsController(
            ProductsService productsService){
        this.productsService = productsService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return productsService.testLogin();
    }

    @Operation(summary = "2.新增商品", description = "新增商品資料")
    @PostMapping("/insert")
    public ResponseEntity<?> insert(
            @Valid
            @RequestBody
            ProductsRequest request) {
        return productsService.insert(request);
    }

    @Operation(summary = "3.查詢商品", description = "查詢商品資料")
    @PostMapping("/select")
    public ResponseEntity<?> select(
            @Valid
            @RequestBody
            QueryProductsRequest request) {
        return productsService.select(request);
    }

    @Operation(summary = "4.更新商品", description = "更新商品資料")
    @PutMapping("/update")
    public ResponseEntity<?> update(
            @Valid
            @RequestBody
            UpdateProductsRequest request) {
        return productsService.update(request);
    }

    @Operation(summary = "5.刪除商品", description = "刪除商品資料")
    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(
            @Valid
            @RequestBody
            DeleteProductsRequest request) {
        return productsService.delete(request);
    }

    @Operation(summary = "批次新增商品", description = "批次新增商品資料")
    @PostMapping("/uploadFile")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file")
            MultipartFile request) {
        return productsService.uploadFile(request);
    }

}
