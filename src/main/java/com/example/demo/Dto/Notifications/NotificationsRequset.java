package com.example.demo.Dto.Notifications;

import com.example.demo.Dto.User.OrderRequest;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder(
        {
                "username",
                "token"
        }
)
@Schema(description = "使用者請求")
public class NotificationsRequset implements OrderRequest {

    @Schema(description = "使用者帳號", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
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
