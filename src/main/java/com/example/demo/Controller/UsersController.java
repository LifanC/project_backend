package com.example.demo.Controller;

import com.example.demo.Dto.User.*;
import com.example.demo.Service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User API", description = "使用者訂單相關功能")
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

}
