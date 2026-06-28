package com.example.demo.Dto.User;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public interface OrderRequest {

    @Schema(description = "使用者帳號不可為空", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "帳號不可為空")
    String getUsername();

}
