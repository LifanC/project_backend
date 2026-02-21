package com.example.demo.Dto.User;

import io.micrometer.common.util.StringUtils;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

public class UserData {

    private String username;
    private String password;
    private String message;
    private HttpStatus status;
    private String permissions;
    private LocalDateTime created_date;
    private LocalDateTime updated_date;
    private String token;
    private List<String> data;

    public UserData() {
    }

    public UserData(String username) {
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
        return StringUtils.isBlank(permissions) ? "" : permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<String> getData() {
        return data;
    }

    public void setData(List<String> data) {
        this.data = data;
    }
}
