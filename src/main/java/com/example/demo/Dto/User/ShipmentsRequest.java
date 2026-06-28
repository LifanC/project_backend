package com.example.demo.Dto.User;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "orderId",
                "trackingNumber",
                "datePart",
                "shipmentsStatus",
                "paymentsStatus",
                "paymentsMethod"
        }
)
@Schema(description = "出貨")
public class ShipmentsRequest implements OrderRequest {

    private String username;

    private String token;

    private String orderId;

    private String trackingNumber;

    private String datePart;

    private String shipmentsStatus;

    private String paymentsStatus;

    private String paymentsMethod;

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

    public String getOrderId() {
        return orderId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getDatePart() {
        return datePart;
    }

    public String getShipmentsStatus() {
        return shipmentsStatus;
    }

    public String getPaymentsStatus() {
        return paymentsStatus;
    }

    public String getPaymentsMethod() {
        return paymentsMethod;
    }
}
