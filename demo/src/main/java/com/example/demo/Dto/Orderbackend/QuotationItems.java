package com.example.demo.Dto.Orderbackend;

import java.math.BigDecimal;

public class QuotationItems {

    BigDecimal quotation_id;

    BigDecimal product_id;

    BigDecimal quantity;

    BigDecimal price;

    BigDecimal unit_percent;

    BigDecimal unit_price;

    public QuotationItems() {
    }

    public QuotationItems(BigDecimal quotation_id) {
        this.quotation_id = quotation_id;
    }

    public BigDecimal getQuotation_id() {
        return quotation_id;
    }

    public void setQuotation_id(BigDecimal quotation_id) {
        this.quotation_id = quotation_id;
    }

    public BigDecimal getProduct_id() {
        return product_id;
    }

    public void setProduct_id(BigDecimal product_id) {
        this.product_id = product_id;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getUnit_percent() {
        return unit_percent;
    }

    public void setUnit_percent(BigDecimal unit_percent) {
        this.unit_percent = unit_percent;
    }

    public BigDecimal getUnit_price() {
        return unit_price;
    }

    public void setUnit_price(BigDecimal unit_price) {
        this.unit_price = unit_price;
    }
}
