package com.example.demo.Dto.Orderbackend;

import java.math.BigDecimal;

public class UserDataSend {

    private String username;

    private BigDecimal quotation_id;

    private String status;

    private String orderItem;

    public UserDataSend() {
    }

    public UserDataSend(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getQuotation_id() {
        return quotation_id;
    }

    public void setQuotation_id(BigDecimal quotation_id) {
        this.quotation_id = quotation_id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(String orderItem) {
        this.orderItem = orderItem;
    }
}
