package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;

public class QueryOrderItemRequest implements OrderRequest {

    private String username;

    @NotBlank(message = "Token不可為空")
    private String token;

    @Override
    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }
}
