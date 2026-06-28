package com.example.demo.Dto.Permissions;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder(
        {
                "username",
                "password",
                "permissions"
        }
)
@Schema(description = "使用者請求")
public class PermissionRequest {

    @Schema(description = "使用者帳號不可為空", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "帳號不可為空")
    private String username;

    @Schema(description = "使用者密碼不可為空", example = "123456", minLength = 6, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密碼不可為空")
    @Size(min = 6, max = 100)
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "密碼需包含英文與數字"
    )
    private String password;

    @Schema(description = "使用者權限不可為空", example = "", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "權限不可為空")
    private String permissions;

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
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }
}
