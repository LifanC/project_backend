package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;

public class QuotationsProductRequest implements OrderRequest {

    private String username;

    private String token;

    @NotBlank(message = "報價單編號不可為空")
    private String quotation_id;

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

    public String getQuotation_id() {
        return quotation_id;
    }
}
