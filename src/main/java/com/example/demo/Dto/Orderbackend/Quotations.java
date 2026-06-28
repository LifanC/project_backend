package com.example.demo.Dto.Orderbackend;

import java.math.BigDecimal;

public class Quotations {

    BigDecimal quotation_id;

    private String username;

    private String status;

    private BigDecimal total_price;

    public Quotations() {
    }

    public Quotations(BigDecimal quotation_id) {
        this.quotation_id = quotation_id;
    }

    public BigDecimal getQuotation_id() {
        return quotation_id;
    }

    public void setQuotation_id(BigDecimal quotation_id) {
        this.quotation_id = quotation_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotal_price() {
        return total_price;
    }

    public void setTotal_price(BigDecimal total_price) {
        this.total_price = total_price;
    }
}
