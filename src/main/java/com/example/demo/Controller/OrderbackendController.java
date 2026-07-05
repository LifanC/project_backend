package com.example.demo.Controller;

import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Dto.User.*;
import com.example.demo.Service.OrderbackendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orderbackend API", description = "訂單後端相關功能")
@RestController
@RequestMapping("/v1/orderbackend")
@Validated
public class OrderbackendController {

    private final OrderbackendService orderbackendService;

    public OrderbackendController(
            OrderbackendService orderbackendService){
        this.orderbackendService = orderbackendService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return orderbackendService.testLogin();
    }

    @Operation(summary = "2.takeToken", description = "取Token")
    @PostMapping("/takeToken")
    public ResponseEntity<?> takeToken(
            @Valid
            @RequestBody
            UserRequest request) {
        return orderbackendService.takeToken(request);
    }

    @Operation(summary = "3.validate", description = "驗證Token")
    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @Valid
            @RequestBody
            UserTokenValidateRequest request) {
        return orderbackendService.validate(request);
    }

    @Operation(summary = "4.logout", description = "登出Token")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Valid
            @RequestBody
            QueryUserRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.logout(request);
    }

    @Operation(summary = "2.查詢用戶名單", description = " ")
    @PostMapping("/queryUser")
    public ResponseEntity<?> queryUser(
            @Valid
            @RequestBody
            QueryUserRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.queryUser(request);
    }

}
