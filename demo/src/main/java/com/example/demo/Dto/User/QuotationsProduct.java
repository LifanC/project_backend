package com.example.demo.Dto.User;

import java.math.BigDecimal;
import java.util.List;

public class QuotationsProduct {

    private BigDecimal quotation_id;

    private String username;

    private List<String> statuss;

    public QuotationsProduct() {
    }

    public QuotationsProduct(BigDecimal quotation_id) {
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

    public List<String> getStatuss() {
        return statuss;
    }

    public void setStatuss(List<String> statuss) {
        this.statuss = statuss;
    }
}
