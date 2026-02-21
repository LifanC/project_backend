package com.example.demo.Dto.User;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

public class UserdataDetails {

    private String username;
    private String password;
    private String message;
    private HttpStatus status;
    private String permissions;
    private String token;
    private List<String> order_item;
    private String order_item_str;
    private LocalDateTime created_date;
    private LocalDateTime updated_date;
    private String action_type;
    private List<String> history;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<String> getOrder_item() {
        return order_item;
    }

    public void setOrder_item(List<String> order_item) {
        this.order_item = order_item;
    }

    public String getOrder_item_str() {
        return order_item_str;
    }

    public void setOrder_item_str(String order_item_str) {
        this.order_item_str = order_item_str;
    }

    public LocalDateTime getCreated_date() {
        return created_date;
    }

    public void setCreated_date(LocalDateTime created_date) {
        this.created_date = created_date;
    }

    public LocalDateTime getUpdated_date() {
        return updated_date;
    }

    public void setUpdated_date(LocalDateTime updated_date) {
        this.updated_date = updated_date;
    }

    public String getAction_type() {
        return action_type;
    }

    public void setAction_type(String action_type) {
        this.action_type = action_type;
    }

    public List<String> getHistory() {
        return history;
    }

    public void setHistory(List<String> history) {
        this.history = history;
    }
}
