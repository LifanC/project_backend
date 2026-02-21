package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class UpdateOrderItemRequest implements OrderRequest {

    private String username;
    @NotEmpty(message = "訂單不可為空")

    private List<@NotBlank(message = "訂單內容不可為空") String> order_item;

    @NotBlank(message = "Token不可為空")
    private String token;

    @NotBlank(message = "更改帳號不可為空")
    private String useruser;

    @Override
    public String getUsername() {
        return username;
    }

    public List<String> getOrder_item() {
        return order_item;
    }

    public String getToken() {
        return token;
    }

    public String getUseruser() {
        return useruser;
    }
}
