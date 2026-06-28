package com.example.demo.Dto.User;

import io.micrometer.common.util.StringUtils;

public class UserData {

    private String username;
    private String password;
    private String permissions;

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

    public String getPermissions() {
        return StringUtils.isBlank(permissions) ? "" : permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

}
