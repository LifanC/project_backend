package com.example.demo.Controller.User;

import com.example.demo.Dto.User.*;
import com.example.demo.Service.UserQuotation.UserServiceQuotation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User Quotation API", description = "使用者估價相關功能")
@RestController
@RequestMapping("/v1/user/quotation")
@Validated
public class UsersQuotationsController {

    private final UserServiceQuotation userService;

    public UsersQuotationsController(
            UserServiceQuotation userService) {
        this.userService = userService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return userService.testLogin();
    }

    @Operation(summary = "2.查詢報價單編號", description = " ")
    @PostMapping("/quotationsProductId")
    public ResponseEntity<?> quotationsProductId(
            @Valid
            @RequestBody
            QuotationsProductIdRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.quotationsProductId(request);
    }

    @Operation(summary = "3.查詢報價單", description = " ")
    @PostMapping("/quotationsProduct")
    public ResponseEntity<?> quotationsProduct(
            @Valid
            @RequestBody
            QuotationsProductRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.quotationsProduct(request);
    }

    @Operation(summary = "4.確認金額", description = " ")
    @PostMapping("/userAccepted")
    public ResponseEntity<?> userAccepted(
            @Valid
            @RequestBody
            QuotationsProductRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.userAccepted(request);
    }

    @Operation(summary = "5.確認金額", description = " ")
    @PostMapping("/userRejected")
    public ResponseEntity<?> userRejected(
            @Valid
            @RequestBody
            QuotationsProductRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.userRejected(request);
    }

}
