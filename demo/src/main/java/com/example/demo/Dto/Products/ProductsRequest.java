package com.example.demo.Dto.Products;

import jakarta.validation.constraints.NotBlank;

public class ProductsRequest {

    @NotBlank(message = "商品名稱不可為空")
    private String products_name;

    @NotBlank(message = "價格不可為空")
    private String price;

    @NotBlank(message = "庫存量不可為空")
    private String stock;

    @NotBlank(message = "描述不可為空")
    private String description;

    public String getProducts_name() {
        return products_name;
    }

    public void setProducts_name(String products_name) {
        this.products_name = products_name;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getStock() {
        return stock;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
