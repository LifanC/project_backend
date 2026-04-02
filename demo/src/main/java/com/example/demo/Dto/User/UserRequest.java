package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;

public class UserRequest implements OrderRequest {

    private String username;

    @NotBlank(message = "密碼不可為空")
    private String password;

    @Override
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
