package com.example.demo.Dto.Orderbackend;

public class UserUser {

    private String username;
    private String orderItem;

    public UserUser() {
    }

    public UserUser(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOrderItem() {
        return orderItem;
    }

    public void setOrderItem(String orderItem) {
        this.orderItem = orderItem;
    }
}
