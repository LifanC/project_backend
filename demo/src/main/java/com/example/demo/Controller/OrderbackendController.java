package com.example.demo.Controller;

import com.example.demo.Dto.Orderbackend.DeleteQuotationsProductItemRequest;
import com.example.demo.Dto.Orderbackend.QueryQuotationsProductItemRequest;
import com.example.demo.Dto.Orderbackend.QuotationsProductItemRequest;
import com.example.demo.Dto.Orderbackend.SendQuotationsProductItemRequest;
import com.example.demo.Dto.User.*;
import com.example.demo.Service.OrderbackendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/orderbackend")
@Validated
public class OrderbackendController {

    private final OrderbackendService orderbackendService;

    public OrderbackendController(
            OrderbackendService orderbackendService){
        this.orderbackendService = orderbackendService;
    }

    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return orderbackendService.testLogin();
    }

    @PostMapping("/takeToken")
    public ResponseEntity<?> takeToken(
            @Valid
            @RequestBody
            UserRequest request) {
        return orderbackendService.takeToken(request);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @Valid
            @RequestBody
            UserTokenValidateRequest request) {
        return orderbackendService.validate(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Valid
            @RequestBody
            QueryUserRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.logout(request);
    }

    @PostMapping("/queryUser")
    public ResponseEntity<?> queryUser(
            @Valid
            @RequestBody
            QueryUserRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.queryUser(request);
    }

    @PostMapping("/quotationsProductItem")
    public ResponseEntity<?> quotationsProductItem(
            @Valid
            @RequestBody
            QuotationsProductItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.quotationsProductItem(request);
    }

    @PostMapping("/confirmQuotationsProductItem")
    public ResponseEntity<?> confirmQuotationsProductItem(
            @Valid
            @RequestBody
            QuotationsProductItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.confirmQuotationsProductItem(request);
    }

    @PostMapping("/deleteQuotationsProduct")
    public ResponseEntity<?> deleteQuotationsProduct(
            @Valid
            @RequestBody
            DeleteQuotationsProductItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.deleteQuotationsProduct(request);
    }

    @PostMapping("/queryQuotationsProduct")
    public ResponseEntity<?> queryQuotationsProduct(
            @Valid
            @RequestBody
            QueryQuotationsProductItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.queryQuotationsProduct(request);
    }

    @PostMapping("/sendQuotationsProduct")
    public ResponseEntity<?> sendQuotationsProduct(
            @Valid
            @RequestBody
            SendQuotationsProductItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.sendQuotationsProduct(request);
    }

}
