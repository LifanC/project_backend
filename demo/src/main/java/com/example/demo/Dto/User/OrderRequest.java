package com.example.demo.Dto.User;

import jakarta.validation.constraints.NotBlank;

public interface OrderRequest {

    @NotBlank(message = "帳號不可為空")
    String getUsername();

}
