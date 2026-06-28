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

@Tag(name = "User Car API", description = "使用者購物車相關功能")
@RestController
@RequestMapping("/v1/user/car")
@Validated
public class UsersCarsController {

    private final UserService userService;

    public UsersCarsController(
            UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return userService.testLogin();
    }

    @Operation(summary = "2.查詢商品", description = " ")
    @PostMapping("/productsCarSelect")
    public ResponseEntity<?> productsCarSelect(
            @Valid
            @RequestBody
            QueryCarItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.productsCarSelect(request);
    }

    @Operation(summary = "3.新增購物車", description = " ")
    @PostMapping("/createCarItem")
    public ResponseEntity<?> createCarItem(
            @Valid
            @RequestBody
            CreateCarItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.createCarItem(request);
    }

    @Operation(summary = "4.查詢購物車", description = " ")
    @PostMapping("/queryCarItem")
    public ResponseEntity<?> queryCarItem(
            @Valid
            @RequestBody
            QueryCarItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.queryCarItem(request);
    }

    @Operation(summary = "5.更改購物車", description = " ")
    @PostMapping("/updateCarItem")
    public ResponseEntity<?> updateCarItem(
            @Valid
            @RequestBody
            UpdateCarItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.updateCarItem(request);
    }

    @Operation(summary = "6.刪除購物車", description = " ")
    @PostMapping("/deleteCarItem")
    public ResponseEntity<?> deleteCarItem(
            @Valid
            @RequestBody
            DeleteCarItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.deleteCarItem(request);
    }

    @Operation(summary = "7.送出報價單", description = " ")
    @PostMapping("/confirmItem")
    public ResponseEntity<?> confirmItem(
            @Valid
            @RequestBody
            ConfirmItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.confirmItem(request);
    }

}
