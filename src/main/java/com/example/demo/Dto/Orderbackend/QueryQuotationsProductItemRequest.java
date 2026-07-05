package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "useruser",
        }
)
@Schema(description = "後端")
public class QueryQuotationsProductItemRequest implements OrderRequest {

    private String username;

    private String token;

    @Schema(description = "用戶帳號不可為空", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用戶帳號不可為空")
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
