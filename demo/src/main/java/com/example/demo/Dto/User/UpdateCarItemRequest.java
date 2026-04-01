package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateCarItemRequest implements OrderRequest {

    private String username;

    private String token;

    @NotBlank(message = "商品編號不可為空")
    private String product_id;

    @NotBlank(message = "商品數量不可為空")
    @Pattern(
            regexp = "^[1-9]\\d*$",
            message = "商品數量不可小於1且不包含英文、中文與小數"
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
