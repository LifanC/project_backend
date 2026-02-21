package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;

public class UserTokenValidateRequest {

    @NotBlank(message = "帳號不可為空")
    private String username;

    public String getUsername() {
        return username;
    }
}
