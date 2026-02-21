package com.example.demo.Dto.User;

import io.micrometer.common.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

public class UserResponse {

    private final String username;
    private final String message;
    private final String permissions;
    private final int status;
    private final LocalDateTime created_date;
    private final LocalDateTime updated_date;
    private final String token;
    private final List<String> data;

    public UserResponse(UserData userData) {
        this.username = userData.getUsername();
        this.message = userData.getMessage();
        this.permissions = userData.getPermissions();
        this.status = userData.getStatus().value();
        this.created_date = userData.getCreated_date();
        this.updated_date = userData.getUpdated_date();
        this.token = userData.getToken();
        this.data = userData.getData();
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return StringUtils.isNotBlank(message) ? message : "";
    }

    public String getPermissions() {
        return permissions;
    }

    public int getStatus() {
        return status;
    }

    public LocalDateTime getCreated_date() {
        return created_date;
    }

    public LocalDateTime getUpdated_date() {
        return updated_date;
    }

    public String getToken() {
        return token;
    }

    public List<String> getData() {
        return data;
    }
}

