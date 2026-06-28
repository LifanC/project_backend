package com.example.demo.Dto.Products;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder(
        {
                "product_id"
        }
)
@Schema(description = "商品請求")
public class QueryProductsRequest {

    @Schema(description = "商品編號", example = "1")
    private String product_id;

    public String getProduct_id() {
        return product_id;
    }

}
