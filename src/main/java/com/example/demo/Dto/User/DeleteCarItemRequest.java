package com.example.demo.Dto.User;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@JsonPropertyOrder(
        {
                "username",
                "token",
                "product_id"
        }
)
@Schema(description = "購物車")
public class DeleteCarItemRequest implements OrderRequest {

    private String username;

    private String token;

    @Schema(description = "商品編號", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品編號不可為空")
    private String product_id;

    @Override
    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public void setAuthHeader(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        if ("Bearer".equals(token.trim())) {
            throw new RuntimeException(username + " - Token 不可為空");
        }
        this.token = token;
    }

    public String getProduct_id() {
        return product_id;
    }

}
