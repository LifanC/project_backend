package com.example.demo.Controller;

import com.example.demo.Dto.User.*;
import com.example.demo.Service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Validated
public class UsersController {

    @Resource
    private UserService userService;

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
            QueryUserRequest request) {
        return userService.logout(request);
    }

    @PostMapping("/queryUser")
    public ResponseEntity<?> queryUser(
            @Valid
            @RequestBody
            QueryUserRequest request) {
        return userService.queryUser(request);
    }

    @PostMapping("/createOrderItem")
    public ResponseEntity<?> createOrderItem(
            @Valid
            @RequestBody
            CreateOrderItemRequest request) {
        return userService.createOrderItem(request);
    }

    @PostMapping("/queryOrderItem")
    public ResponseEntity<?> queryOrderItem(
            @Valid
            @RequestBody
            QueryOrderItemRequest request) {
        return userService.queryOrderItem(request);
    }

    @PostMapping("/updateOrderItem")
    public ResponseEntity<?> updateOrderItem(
            @Valid
            @RequestBody
            UpdateOrderItemRequest request) {
        return userService.updateOrderItem(request);
    }

    @PostMapping("/deleteOrderItem")
    public ResponseEntity<?> deleteOrderItem(
            @Valid
            @RequestBody
            DeleteOrderItemRequest request) {
        return userService.deleteOrderItem(request);
    }

    @PostMapping("/historyOrderItem")
    public ResponseEntity<?> historyOrderItem(
            @Valid
            @RequestBody
            QueryOrderItemRequest request) {
        return userService.historyOrderItem(request);
    }
}
