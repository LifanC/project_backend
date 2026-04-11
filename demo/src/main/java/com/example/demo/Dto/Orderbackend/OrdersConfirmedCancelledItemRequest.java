package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;
import jakarta.validation.constraints.NotBlank;

public class OrdersConfirmedCancelledItemRequest implements OrderRequest {

    private String username;

    private String token;

    @Override
    public String getUsername() {
        return username;
    }

    @NotBlank(message = "用戶帳號不可為空")
    private String useruser;

    @NotBlank(message = "訂單編號不可為空")
    private String ordersId;

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

    public String getOrdersId() {
        return ordersId;
    }
}
