package com.example.demo.Controller;

import com.example.demo.Dto.User.*;
import com.example.demo.Service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User API", description = "使用者訂單相關功能")
@RestController
@RequestMapping("/v1/user")
@Validated
public class UsersController {

    private final UserService userService;

    public UsersController(
            UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return userService.testLogin();
    }

    @Operation(summary = "2.takeToken", description = "取Token")
    @PostMapping("/takeToken")
    public ResponseEntity<?> takeToken(
            @Valid
            @RequestBody
            UserRequest request) {
        return userService.takeToken(request);
    }

    @Operation(summary = "3.validate", description = "驗證Token")
    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @Valid
            @RequestBody
            UserTokenValidateRequest request) {
        return userService.validate(request);
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
        return userService.logout(request);
    }

}
