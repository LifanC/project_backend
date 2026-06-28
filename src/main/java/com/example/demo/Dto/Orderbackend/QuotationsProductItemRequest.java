package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class QuotationsProductItemRequest implements OrderRequest {

    private String username;

    private String token;

    @NotBlank(message = "用戶帳號不可為空")
    private String useruser;

    @Pattern(
            regexp = "^[1-9]\\d?$",
            message = "銷售%數需為1~99之間的整數"
    )
    private String userPercent;

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

    public String getUseruser() {
        return useruser;
    }

    public String getUserPercent() {
        return userPercent;
    }
}
