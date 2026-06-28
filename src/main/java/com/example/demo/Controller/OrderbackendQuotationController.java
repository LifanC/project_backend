package com.example.demo.Controller;

import com.example.demo.Dto.Orderbackend.*;
import com.example.demo.Service.OrderbackendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orderbackend Quotation API", description = "訂單後端相關功能")
@RestController
@RequestMapping("/v1/orderbackend/quotation")
@Validated
public class OrderbackendQuotationController {

    private final OrderbackendService orderbackendService;

    public OrderbackendQuotationController(
            OrderbackendService orderbackendService){
        this.orderbackendService = orderbackendService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return orderbackendService.testLogin();
    }

    @Operation(summary = "2.用戶商品報價單", description = " ")
    @PostMapping("/quotationsProductItem")
    public ResponseEntity<?> quotationsProductItem(
            @Valid
            @RequestBody
            QuotationsProductItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.quotationsProductItem(request);
    }

    @Operation(summary = "3.確認報價單", description = " ")
    @PostMapping("/confirmQuotationsProductItem")
    public ResponseEntity<?> confirmQuotationsProductItem(
            @Valid
            @RequestBody
            QuotationsProductItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.confirmQuotationsProductItem(request);
    }

    @Operation(summary = "4.刪除報價單", description = " ")
    @PostMapping("/deleteQuotationsProduct")
    public ResponseEntity<?> deleteQuotationsProduct(
            @Valid
            @RequestBody
            DeleteQuotationsProductItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.deleteQuotationsProduct(request);
    }

    @Operation(summary = "5.查詢報價單", description = " ")
    @PostMapping("/queryQuotationsProduct")
    public ResponseEntity<?> queryQuotationsProduct(
            @Valid
            @RequestBody
            QueryQuotationsProductItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.queryQuotationsProduct(request);
    }

    @Operation(summary = "6.送出報價單", description = " ")
    @PostMapping("/sendQuotationsProduct")
    public ResponseEntity<?> sendQuotationsProduct(
            @Valid
            @RequestBody
            SendQuotationsProductItemRequest request,
            @Schema(description = "token", example = "token_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.sendQuotationsProduct(request);
    }

}
