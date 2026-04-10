package com.example.demo.Dto.Orderbackend;

import java.math.BigDecimal;

public class OrderItems {

    BigDecimal order_id;

    BigDecimal product_id;

    BigDecimal quantity;

    BigDecimal price;

    public OrderItems() {
    }

    public OrderItems(BigDecimal order_id) {
        this.order_id = order_id;
    }

    public BigDecimal getOrder_id() {
        return order_id;
    }

    public void setOrder_id(BigDecimal order_id) {
        this.order_id = order_id;
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
}
