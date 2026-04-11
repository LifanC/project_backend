package com.example.demo.Dto.User;

import java.math.BigDecimal;
import java.util.List;

public class Orders {

    private BigDecimal order_id;

    private BigDecimal quotation_id;

    private String username;

    private String status;

    private BigDecimal total_price;

    private List<String> quotationsStatuss;

    private List<String> statuss;

    public Orders() {
    }

    public Orders(BigDecimal order_id) {
        this.order_id = order_id;
    }

    public Orders(BigDecimal order_id, BigDecimal quotation_id) {
        this.order_id = order_id;
        this.quotation_id = quotation_id;
    }

    public BigDecimal getOrder_id() {
        return order_id;
    }

    public void setOrder_id(BigDecimal order_id) {
        this.order_id = order_id;
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

    public List<String> getQuotationsStatuss() {
        return quotationsStatuss;
    }

    public void setQuotationsStatuss(List<String> quotationsStatuss) {
        this.quotationsStatuss = quotationsStatuss;
    }

    public List<String> getStatuss() {
        return statuss;
    }

    public void setStatuss(List<String> statuss) {
        this.statuss = statuss;
    }
}
