package com.example.demo.Controller.User;

import com.example.demo.Dto.User.ShipmentsRequest;
import com.example.demo.Service.UserShipment.UserServiceShipment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User Shipment API", description = "使用者訂單相關功能")
@RestController
@RequestMapping("/v1/user/shipment")
@Validated
public class UsersShipmentsController {

    private final UserServiceShipment userService;

    public UsersShipmentsController(
            UserServiceShipment userService) {
        this.userService = userService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return userService.testLogin();
    }

    @Operation(summary = "2.查詢出貨資訊", description = " ")
    @PostMapping("/userShipments")
    public ResponseEntity<?> userShipments(
            @Valid
            @RequestBody
            ShipmentsRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.userShipments(request);
    }

}
