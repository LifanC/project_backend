package com.example.demo.Dto.User;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "product_id"
        }
)
@Schema(description = "購物車")
public class QueryCarItemRequest implements OrderRequest {

    private String username;

    private String token;

    private String product_id;

    private String[] product_ids;

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

    public String[] getProduct_ids() {
        return product_ids;
    }
}
