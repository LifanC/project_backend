package com.example.demo.Service;

import com.example.demo.Dto.Products.DeleteProductsRequest;
import com.example.demo.Dto.Products.ProductsRequest;
import com.example.demo.Dto.Products.QueryProductsRequest;
import com.example.demo.Dto.Products.UpdateProductsRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface ProductsService {
    ResponseEntity<?> testLogin();

    ResponseEntity<?> insert(@Valid ProductsRequest request);

    ResponseEntity<?> select(@Valid QueryProductsRequest request);

    ResponseEntity<?> update(@Valid UpdateProductsRequest request);

    ResponseEntity<?> delete(@Valid DeleteProductsRequest request);
}
