package com.example.demo.Dto.Orderbackend;

import java.math.BigDecimal;

public class Payments {

    private BigDecimal order_id;

    private String username;

    private BigDecimal amount;

    private String status;

    private String payments_method;

    public Payments() {
    }

    public Payments(BigDecimal order_id) {
        this.order_id = order_id;
    }

    public BigDecimal getOrder_id() {
        return order_id;
    }

    public void setOrder_id(BigDecimal order_id) {
        this.order_id = order_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayments_method() {
        return payments_method;
    }

    public void setPayments_method(String payments_method) {
        this.payments_method = payments_method;
    }
}
