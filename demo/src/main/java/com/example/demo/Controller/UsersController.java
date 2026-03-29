package com.example.demo.Controller;

import com.example.demo.Aspect.Permissions;
import com.example.demo.Dto.User.*;
import com.example.demo.Security.Annotation.CheckRole;
import com.example.demo.Service.UserService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
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
    @PermitAll
    public ResponseEntity<?> takeToken(
            @Valid
            @RequestBody
            UserRequest request) {
        return userService.takeToken(request);
    }

    @PostMapping("/validate")
    @PermitAll
    public ResponseEntity<?> validate(
            @Valid
            @RequestBody
            UserTokenValidateRequest request) {
        return userService.validate(request);
    }

    @PostMapping("/logout")
    @PermitAll
    public ResponseEntity<?> logout(
            @Valid
            @RequestBody
            QueryUserRequest request) {
        return userService.logout(request);
    }

    @PostMapping("/queryUser")
    @CheckRole(Permissions.USER_ITEM_QUERY)
    public ResponseEntity<?> queryUser(
            @Valid
            @RequestBody
            QueryUserRequest request) {
        return userService.queryUser(request);
    }

    @PostMapping("/createOrderItem")
    @CheckRole(Permissions.ORDER_ITEM_CREATE)
    public ResponseEntity<?> createOrderItem(
            @Valid
            @RequestBody
            CreateOrderItemRequest request) {
        return userService.createOrderItem(request);
    }

    @PostMapping("/queryOrderItem")
    @CheckRole(Permissions.ORDER_ITEM_QUERY)
    public ResponseEntity<?> queryOrderItem(
            @Valid
            @RequestBody
            QueryOrderItemRequest request) {
        return userService.queryOrderItem(request);
    }

    @PostMapping("/updateOrderItem")
    @CheckRole(Permissions.ORDER_ITEM_UPDATE)
    public ResponseEntity<?> updateOrderItem(
            @Valid
            @RequestBody
            UpdateOrderItemRequest request) {
        return userService.updateOrderItem(request);
    }

    @PostMapping("/deleteOrderItem")
    @CheckRole(Permissions.ORDER_ITEM_DELETE)
    public ResponseEntity<?> deleteOrderItem(
            @Valid
            @RequestBody
            DeleteOrderItemRequest request) {
        return userService.deleteOrderItem(request);
    }

    @PostMapping("/historyOrderItem")
    @CheckRole(Permissions.ORDER_ITEM_HISTORY)
    public ResponseEntity<?> historyOrderItem(
            @Valid
            @RequestBody
            QueryOrderItemRequest request) {
        return userService.historyOrderItem(request);
    }
}
