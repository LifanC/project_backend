package com.example.demo.Dto.Orderbackend;

import com.example.demo.Dto.User.OrderRequest;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "useruser",
                "orderId",
                "trackingNumber",
                "datePart"
        }
)
@Schema(description = "後端")
public class ShipmentsItemRequest implements OrderRequest {

    private String username;

    private String token;

    private String useruser;

    private String orderId;

    private String trackingNumber;

    private String datePart;

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

    public String getOrderId() {
        return orderId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getDatePart() {
        return datePart;
    }
}
