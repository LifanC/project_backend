package com.example.demo.controller;

import com.example.demo.Service.UserService;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/auth/")
public class UsersController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private UserService userService;

    @GetMapping("testLogin")
    public String login() {
        logger.info("auth/login: success");
        return "auth/login: success";
    }

    @PostMapping("register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody
            Map<String, Object> map,
            HttpServletRequest request) {
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            throw new RuntimeException("註冊 帳號密碼未輸入");
        }

        Map<String, Object> ui = userService.register(map);
        return getMapResponseEntity(request, ui);
    }

    @GetMapping("login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam
            String username,
            String password,
            HttpServletRequest request) {
        if (StringUtils.isBlank(username)
                || StringUtils.isBlank(password)) {
            throw new RuntimeException("登入 帳號密碼未輸入");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("username", username);
        map.put("password", password);
        Map<String, Object> ui = userService.login(map);
        return getMapResponseEntity(request, ui);
    }

    @PutMapping("updatePassword")
    public ResponseEntity<Map<String, Object>> updatePassword(
            @RequestBody
            Map<String, Object> map,
            HttpServletRequest request) {
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            throw new RuntimeException("登入 帳號密碼未輸入");
        }

        Map<String, Object> ui = userService.updatePassword(map);
        return getMapResponseEntity(request, ui);
    }

    @GetMapping("validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestParam String token,
            HttpServletRequest request) {
        if (StringUtils.isBlank(token)) {
            throw new RuntimeException("驗證 token未輸入");
        }

        Map<String, Object> ui = userService.validateToken(token);
        return getMapResponseEntity(request, ui);
    }

    @PostMapping("query")
    public ResponseEntity<Map<String, Object>> query(
            @RequestBody
            Map<String, Object> map,
            HttpServletRequest request) {
        if (StringUtils.isBlank(map.get("username").toString())
                || StringUtils.isBlank(map.get("password").toString())) {
            throw new RuntimeException("查詢 帳號密碼未輸入");
        }

        Map<String, Object> ui = userService.query(map);
        return getMapResponseEntity(request, ui);
    }

    @DeleteMapping("logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestBody
            Map<String, Object> map,
            HttpServletRequest request) {
        if (StringUtils.isBlank(map.get("token").toString())) {
            throw new RuntimeException("登出 token未輸入");
        }

        Map<String, Object> ui = userService.logout(map);
        return getMapResponseEntity(request, ui);
    }

    private ResponseEntity<Map<String, Object>> getMapResponseEntity(HttpServletRequest request, Map<String, Object> ui) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", HttpStatus.OK.value());
        result.put("detail", ui);
        result.put("path", request.getRequestURI());
        result.put("timestamp", LocalDateTime.now());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(result);
    }

}
