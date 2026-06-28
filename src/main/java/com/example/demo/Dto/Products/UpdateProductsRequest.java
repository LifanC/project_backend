package com.example.demo.Dto.Products;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonPropertyOrder(
        {
                "product_id",
                "products_name",
                "price",
                "stock",
                "description"
        }
)
@Schema(description = "商品請求")
public class UpdateProductsRequest {

    @Schema(description = "商品編號", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品編號不可為空")
    @Pattern(
            regexp = "^\\d+$",
            message = "價格只能是正整數"
    )
    private String product_id;

    private String products_name;

    private String price;

    private String stock;

    private String description;

    public String getProduct_id() {
        return product_id;
    }

    public void setProduct_id(String product_id) {
        this.product_id = product_id;
    }

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
