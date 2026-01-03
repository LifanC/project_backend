package com.example.demo.controller;

import com.example.demo.Service.UserService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/auth/")
public class UsersController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private UserService userService;

    @GetMapping("login")
    public String login() {
        logger.info("login: success");
        return "login: success";
    }

    @PostMapping("register")
    public Map<String, Object> register(@RequestBody Map<String, Object> map) {
        return userService.register(map);
    }

    @PostMapping("login")
    public String login(@RequestBody Map<String, Object> map) {
        return userService.login(map);
    }

    @GetMapping("validate")
    public String validate(@RequestParam String token) {
        boolean validate = userService.validateToken(token);
        return validate ? "有效" : "無效";
    }

    @GetMapping("logout")
    public String logout(@RequestParam String token) {
        userService.logout(token);
        return "已登出";
    }

}
