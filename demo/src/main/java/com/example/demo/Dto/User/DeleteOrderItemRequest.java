package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;

public class DeleteOrderItemRequest implements OrderRequest {

    private String username;

    private String token;

    @NotBlank(message = "刪除帳號不可為空")
    private String useruser;

    @Override
    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public String getUseruser() {
        return useruser;
    }

    public void setAuthHeader(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        if ("Bearer".equals(token.trim())) {
            throw new RuntimeException(username + " - Token 不可為空");
        }
        this.token = token;
    }
}
