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

    @PostMapping("/productsCarSelect")
    public ResponseEntity<?> productsCarSelect(
            @Valid
            @RequestBody
            QueryCarItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.productsCarSelect(request);
    }

    @PostMapping("/createCarItem")
    public ResponseEntity<?> createCarItem(
            @Valid
            @RequestBody
            CreateCarItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.createCarItem(request);
    }

    @PostMapping("/queryCarItem")
    public ResponseEntity<?> queryCarItem(
            @Valid
            @RequestBody
            QueryCarItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.queryCarItem(request);
    }

    @PostMapping("/updateCarItem")
    public ResponseEntity<?> updateCarItem(
            @Valid
            @RequestBody
            UpdateCarItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.updateCarItem(request);
    }

    @PostMapping("/deleteCarItem")
    public ResponseEntity<?> deleteCarItem(
            @Valid
            @RequestBody
            DeleteCarItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.deleteCarItem(request);
    }

    @PostMapping("/confirmItem")
    public ResponseEntity<?> confirmItem(
            @Valid
            @RequestBody
            ConfirmItemRequest request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return userService.confirmItem(request);
    }

}
