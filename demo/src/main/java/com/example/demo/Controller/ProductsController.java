package com.example.demo.Controller;

import com.example.demo.Dto.Products.ProductsRequest;
import com.example.demo.Dto.Products.UpdateProductsRequest;
import com.example.demo.Service.ProductsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/products")
@Validated
public class ProductsController {

    private final ProductsService productsService;

    public ProductsController(
            ProductsService productsService){
        this.productsService = productsService;
    }

    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return productsService.testLogin();
    }

    @PostMapping("/insert")
    public ResponseEntity<?> insert(
            @Valid
            @RequestBody
            ProductsRequest request) {
        return productsService.insert(request);
    }

    @GetMapping("/select")
    public ResponseEntity<?> select() {
        return productsService.select();
    }

    @PutMapping("/update")
    public ResponseEntity<?> update(
            @Valid
            @RequestBody
            UpdateProductsRequest request) {
        return productsService.update(request);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(
            @RequestParam
            String product_id) {
        return productsService.delete(product_id);
    }

}
