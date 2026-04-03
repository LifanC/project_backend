package com.example.demo.Controller;

import com.example.demo.Dto.Orderbackend.QueryQuotationsItemRequest;
import com.example.demo.Dto.Orderbackend.QueryUserProductItemRequest;
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

    @PostMapping("/queryUserProductItem")
    public ResponseEntity<?> queryUserProductItem(
            @Valid
            @RequestBody
            QueryUserProductItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.queryUserProductItem(request);
    }

    @PostMapping("/queryQuotationsItem")
    public ResponseEntity<?> queryQuotationsItem(
            @Valid
            @RequestBody
            QueryQuotationsItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return orderbackendService.queryQuotationsItem(request);
    }

}
