package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;

public class QueryUserProductItemRequest implements OrderRequest {

    private String username;

    private String token;

    private String useruser;

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
}
