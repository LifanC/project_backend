package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "trackingNumber"
        }
)
@Schema(description = "後端")
public class ShipmentsTrackingNumberItemRequest implements OrderRequest {

    private String username;

    private String token;

    @Pattern(
            regexp = "^[A-Za-z]{2}\\d{11}$",
            message = "追蹤號碼格式需為2碼英文+11碼數字（共13碼）"
    )
    private String trackingNumber;

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

    public String getTrackingNumber() {
        return trackingNumber;
    }
}
