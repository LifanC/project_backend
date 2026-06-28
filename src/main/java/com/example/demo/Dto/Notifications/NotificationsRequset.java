package com.example.demo.Dto.Notifications;

import com.example.demo.Dto.User.OrderRequest;

public class NotificationsRequset implements OrderRequest {

    private String username;

    private String token;

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

}
