package com.example.demo.Dto.User;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "product_id"
        }
)
@Schema(description = "購物車")
public class UpdateCarItemRequest implements OrderRequest {

    private String username;

    private String token;

    @Schema(description = "商品編號", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品編號不可為空")
    private String product_id;

    @Schema(description = "商品數量需為1~99之間的整數", example = "99")
    @Pattern(
            regexp = "^[1-9]\\d?$",
            message = "商品數量需為1~99之間的整數"
    )
    private String product_quantity;

    @Override
    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public void setAuthHeader(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        if ("Bearer".equals(token.trim())) {
            throw new RuntimeException(username + " - Token 不可為空");
        }
        this.token = token;
    }

    public String getProduct_id() {
        return product_id;
    }

    public String getProduct_quantity() {
        return product_quantity;
    }
}
