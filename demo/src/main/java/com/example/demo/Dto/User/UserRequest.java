package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserRequest {

    @NotBlank(message = "帳號不可為空")
    private String username;

    @NotBlank(message = "密碼不可為空")
    @Size(min = 6, max = 100)
    private String password;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
