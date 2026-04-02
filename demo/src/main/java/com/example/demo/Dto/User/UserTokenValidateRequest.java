package com.example.demo.Dto.User;

public class UserTokenValidateRequest implements OrderRequest {

    private String username;

    @Override
    public String getUsername() {
        return username;
    }
}
