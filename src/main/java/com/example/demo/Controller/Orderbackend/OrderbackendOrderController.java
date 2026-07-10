package com.example.demo.Controller.Orderbackend;

import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Service.OrderbackendOrder.OrderbackendServiceOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orderbackend Order API", description = "訂單後端相關功能")
@RestController
@RequestMapping("/v1/orderbackend/order")
@Validated
public class OrderbackendOrderController {

    private final OrderbackendServiceOrder orderbackendService;

    public OrderbackendOrderController(
            OrderbackendServiceOrder orderbackendService){
        this.orderbackendService = orderbackendService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return orderbackendService.testLogin();
    }

    @Operation(summary = "2.查詢用戶訂單名單", description = " ")
    @PostMapping("/ordersUser")
    public ResponseEntity<?> ordersUser(
            @Valid
            @RequestBody
            OrdersUserItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.ordersUser(request);
    }

    @Operation(summary = "3.查詢訂單", description = " ")
    @PostMapping("/ordersProduct")
    public ResponseEntity<?> ordersProduct(
            @Valid
            @RequestBody
            OrdersProductItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.ordersProduct(request);
    }

    @Operation(summary = "4.確認訂單", description = " ")
    @PostMapping("/ordersConfirmed")
    public ResponseEntity<?> ordersConfirmed(
            @Valid
            @RequestBody
            OrdersConfirmedCancelledItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.ordersConfirmed(request);
    }

    @Operation(summary = "5.取消訂單", description = " ")
    @PostMapping("/ordersCancelled")
    public ResponseEntity<?> ordersCancelled(
            @Valid
            @RequestBody
            OrdersConfirmedCancelledItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.ordersCancelled(request);
    }

}
