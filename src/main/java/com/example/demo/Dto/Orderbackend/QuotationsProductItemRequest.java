package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "useruser",
                "orderItem",
                "userPercent"
        }
)
@Schema(description = "後端")
public class QuotationsProductItemRequest implements OrderRequest {

    private String username;

    private String token;

    @Schema(description = "用戶帳號不可為空", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用戶帳號不可為空")
    private String useruser;

    @Schema(description = "銷售代號不可為空", example = "1:1,2:1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "銷售代號不可為空")
    private String orderItem;

    @Pattern(
            regexp = "^[1-9]\\d?$",
            message = "銷售%數需為1~99之間的整數"
    )
    private String userPercent;

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

    public String getOrderItem() {
        return orderItem;
    }

    public String getUserPercent() {
        return userPercent;
    }
}
