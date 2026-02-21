package com.example.demo.Dto.Permissions;

import java.time.LocalDateTime;
import java.util.List;

public class PermissionResponse {

    private final String username;
    private final String message;
    private final String permissions;
    private final int status;
    private final LocalDateTime created_date;
    private final LocalDateTime updated_date;
    private final List<String> users;

    public PermissionResponse(Permission permission) {
        this.username = permission.getUsername();
        this.message = permission.getMessage();
        this.permissions = permission.getPermissions();
        this.status = permission.getStatus().value();
        this.created_date = permission.getCreated_date();
        this.updated_date = permission.getUpdated_date();
        this.users = permission.getUsers();
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return message;
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

    public List<String> getUsers() {
        return users;
    }
}
