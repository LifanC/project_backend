package com.example.demo.Controller;

import com.example.demo.Dto.Orderbackend.ShipmentsItemRequest;
import com.example.demo.Dto.Orderbackend.ShipmentsTrackingNumberItemRequest;
import com.example.demo.Service.OrderbackendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orderbackend Shipment API", description = "訂單後端相關功能")
@RestController
@RequestMapping("/v1/orderbackend/shipment")
@Validated
public class OrderbackendShipmentController {

    private final OrderbackendService orderbackendService;

    public OrderbackendShipmentController(
            OrderbackendService orderbackendService){
        this.orderbackendService = orderbackendService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return orderbackendService.testLogin();
    }

    @Operation(summary = "2.查詢用戶出貨名單", description = " ")
    @PostMapping("/shipmentsTrackingNumber")
    public ResponseEntity<?> shipmentsTrackingNumber(
            @Valid
            @RequestBody
            ShipmentsItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.shipmentsTrackingNumber(request);
    }

    @Operation(summary = "3.已出貨", description = " ")
    @PostMapping("/shipmentsShipped")
    public ResponseEntity<?> shipmentsShipped(
            @Valid
            @RequestBody
            ShipmentsTrackingNumberItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.shipmentsShipped(request);
    }

    @Operation(summary = "4.已送達", description = " ")
    @PostMapping("/shipmentsDelivered")
    public ResponseEntity<?> shipmentsDelivered(
            @Valid
            @RequestBody
            ShipmentsTrackingNumberItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.shipmentsDelivered(request);
    }

}
