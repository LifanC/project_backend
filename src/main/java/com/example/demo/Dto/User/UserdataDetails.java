package com.example.demo.Dto.User;

public class UserdataDetails {

    private String username;
    private String order_item_str;
    private String action_type;

    public UserdataDetails() {
    }

    public UserdataDetails(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOrder_item_str() {
        return order_item_str;
    }

    public void setOrder_item_str(String order_item_str) {
        this.order_item_str = order_item_str;
    }

    public String getAction_type() {
        return action_type;
    }

    public void setAction_type(String action_type) {
        this.action_type = action_type;
    }
}
