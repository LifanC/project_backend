package com.example.demo.Controller;

import com.example.demo.Dto.Products.DeleteProductsRequest;
import com.example.demo.Dto.Products.ProductsRequest;
import com.example.demo.Dto.Products.QueryProductsRequest;
import com.example.demo.Dto.Products.UpdateProductsRequest;
import com.example.demo.Service.ProductsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
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

    @PostMapping("/select")
    public ResponseEntity<?> select(
            @Valid
            @RequestBody
            QueryProductsRequest request) {
        return productsService.select(request);
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
            @Valid
            @RequestBody
            DeleteProductsRequest request) {
        return productsService.delete(request);
    }

}
