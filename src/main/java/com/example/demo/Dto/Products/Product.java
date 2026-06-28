package com.example.demo.Dto.Products;

import java.math.BigDecimal;

public class Product {

    private BigDecimal product_id;
    private String products_name;
    private BigDecimal price;
    private BigDecimal stock;
    private String description;
    private BigDecimal product_id_quantity;

    public Product() {
    }

    public Product(BigDecimal product_id) {
        this.product_id = product_id;
    }

    public Product(String products_name, BigDecimal price, BigDecimal stock, String description) {
        this.products_name = products_name;
        this.price = price;
        this.stock = stock;
        this.description = description;
    }

    public BigDecimal getProduct_id() {
        return product_id;
    }

    public void setProduct_id(BigDecimal product_id) {
        this.product_id = product_id;
    }

    public String getProducts_name() {
        return products_name;
    }

    public void setProducts_name(String products_name) {
        this.products_name = products_name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getStock() {
        return stock;
    }

    public void setStock(BigDecimal stock) {
        this.stock = stock;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getProduct_id_quantity() {
        return product_id_quantity;
    }

    public void setProduct_id_quantity(BigDecimal product_id_quantity) {
        this.product_id_quantity = product_id_quantity;
    }
}
