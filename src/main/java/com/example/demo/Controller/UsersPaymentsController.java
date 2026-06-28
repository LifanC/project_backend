package com.example.demo.Controller;

import com.example.demo.Dto.User.PayMoneyRequest;
import com.example.demo.Dto.User.PaymentsRequest;
import com.example.demo.Service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User Payment API", description = "使用者付款相關功能")
@RestController
@RequestMapping("/v1/user/payment")
@Validated
public class UsersPaymentsController {

    private final UserService userService;

    public UsersPaymentsController(
            UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return userService.testLogin();
    }

    @Operation(summary = "2.查詢付款資訊", description = " ")
    @PostMapping("/userPayments")
    public ResponseEntity<?> userPayments(
            @Valid
            @RequestBody
            PaymentsRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.userPayments(request);
    }

    @Operation(summary = "3.付款", description = " ")
    @PostMapping("/userPayMoney")
    public ResponseEntity<?> userPayMoney(
            @Valid
            @RequestBody
            PayMoneyRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.userPayMoney(request);
    }

}
