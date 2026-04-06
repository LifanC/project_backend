package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;
import jakarta.validation.constraints.NotBlank;

public class SendQuotationsProductItemRequest implements OrderRequest {

    private String username;

    private String token;

    @NotBlank(message = "用戶帳號不可為空")
    private String useruser;

    @NotBlank(message = "報價單編號不可為空")
    private String userUserQuotationsId;

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

    public String getUserUserQuotationsId() {
        return userUserQuotationsId;
    }
}
