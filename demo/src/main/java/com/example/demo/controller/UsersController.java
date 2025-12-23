package com.example.demo.controller;

import com.example.demo.Service.UserService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/")
public class UsersController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private UserService userService;

    @GetMapping("login")
    public String login() {
        logger.info("login: success");
        return "login: success";
    }

    @PostMapping("createUser")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        logger.info("data: {}", map);
        boolean judge = userService.createUser(map.get("name").toString(), map.get("data").toString());
        if (judge) {
            result.put("status", "success");
            result.put("message", "users 存取成功");
        } else {
            result.put("status", "fail");
            result.put("message", "users 存取失敗");
        }
        logger.info("createUser: {}", result);
        return result;
    }

    @PostMapping("queryUser")
    public Map<String, Object> queryUser(@RequestBody Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        result.put("data", map);
        Object resultData = userService.query(map.get("name").toString());
        if (resultData != null) {
            result.put("status", "success");
            result.put("message", "users 查詢成功");
            result.put("result", resultData);
        } else {
            result.put("status", "fail");
            result.put("message", "users 查無資料");
        }
        logger.info("queryUser: {}", result);
        return result;
    }


    @PostMapping("deleteUser")
    public Map<String, Object> deleteUser(@RequestBody Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        result.put("data", map);
        int del = userService.delete(map.get("name").toString());
        if (del > 0) {
            result.put("status", "success");
            result.put("message", "users 刪除成功");
        } else {
            result.put("status", "fail");
            result.put("message", "users 查無資料");
        }
        logger.info("deleteUser: {}", result);
        return result;
    }

}
