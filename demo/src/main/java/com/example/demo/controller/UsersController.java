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
        logger.info("auth/login: success");
        return "auth/login: success";
    }

    @PostMapping("register")
    public Map<String, Object> register(@RequestBody Map<String, Object> map) {
        return userService.register(map);
    }

    @PostMapping("updatePassword")
    public Map<String, Object> updatePassword(@RequestBody Map<String, Object> map) {
        return userService.updatePassword(map);
    }

    @PostMapping("login")
    public Map<String, Object> login(@RequestBody Map<String, Object> map) {
        return userService.login(map);
    }

    @GetMapping("validate")
    public Map<String, Object> validate(@RequestParam String token) {
        return userService.validateToken(token);
    }

    @PostMapping("query")
    public Map<String, Object> query(@RequestBody Map<String, Object> map) {
        return userService.query(map);
    }

    @GetMapping("logout")
    public Map<String, Object> logout(@RequestParam String token) {
        return userService.logout(token);
    }

}
