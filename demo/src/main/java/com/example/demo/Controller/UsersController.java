package com.example.demo.Controller;

import com.example.demo.Dto.User.*;
import com.example.demo.Service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/user")
@Validated
public class UsersController {

    private final UserService userService;

    public UsersController(
            UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return userService.testLogin();
    }

    @PostMapping("/takeToken")
    public ResponseEntity<?> takeToken(
            @Valid
            @RequestBody
            UserRequest request) {
        return userService.takeToken(request);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @Valid
            @RequestBody
            UserTokenValidateRequest request) {
        return userService.validate(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Valid
            @RequestBody
            QueryUserRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.logout(request);
    }

    @PostMapping("/queryUser")
    public ResponseEntity<?> queryUser(
            @Valid
            @RequestBody
            QueryUserRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.queryUser(request);
    }

    @PostMapping("/createOrderItem")
    public ResponseEntity<?> createOrderItem(
            @Valid
            @RequestBody
            CreateOrderItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.createOrderItem(request);
    }

    @PostMapping("/queryOrderItem")
    public ResponseEntity<?> queryOrderItem(
            @Valid
            @RequestBody
            QueryOrderItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.queryOrderItem(request);
    }

    @PostMapping("/updateOrderItem")
    public ResponseEntity<?> updateOrderItem(
            @Valid
            @RequestBody
            UpdateOrderItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.updateOrderItem(request);
    }

    @PostMapping("/deleteOrderItem")
    public ResponseEntity<?> deleteOrderItem(
            @Valid
            @RequestBody
            DeleteOrderItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.deleteOrderItem(request);
    }

    @PostMapping("/historyOrderItem")
    public ResponseEntity<?> historyOrderItem(
            @Valid
            @RequestBody
            QueryOrderItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.historyOrderItem(request);
    }
}
