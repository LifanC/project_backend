package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PayMoneyRequest implements OrderRequest {

    private String username;

    private String token;

    @Pattern(
            regexp = "^[A-Za-z]{2}\\d{11}$",
            message = "追蹤號碼格式需為2碼英文+11碼數字（共13碼）"
    )
    private String trackingNumber;

    @Pattern(
            regexp = "^[1-9]\\d*$",
            message = "付款金額需為1以上的整數"
    )
    private String amount;

    @NotBlank(message = "付款方式不可為空")
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

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getAmount() {
        return amount;
    }

    public String getPaymentsMethod() {
        return paymentsMethod;
    }
}
