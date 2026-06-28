package com.example.demo.Dto.Products;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonPropertyOrder(
        {
                "products_name",
                "price",
                "stock",
                "description"
        }
)
@Schema(description = "商品請求")
public class ProductsRequest {

    @Schema(description = "商品名稱", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品名稱不可為空")
    private String products_name;

    @Schema(description = "商品價格", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品價格不可為空")
    @Pattern(
            regexp = "^\\d+$",
            message = "商品價格只能是正整數"
    )
    private String price;

    @Schema(description = "商品庫存", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品庫存量不可為空")
    @Pattern(
            regexp = "^\\d+$",
            message = "商品價格只能是正整數"
    )
    private String stock;

    @Schema(description = "商品描述不可為空", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品描述不可為空")
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
