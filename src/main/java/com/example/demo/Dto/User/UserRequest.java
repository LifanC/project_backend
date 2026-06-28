package com.example.demo.Dto.User;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@JsonPropertyOrder(
        {
                "username",
                "password"
        }
)
@Schema(description = "使用者")
public class UserRequest implements OrderRequest {

    private String username;

    @Schema(description = "使用者密碼不可為空", example = "123456", minLength = 6, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
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
