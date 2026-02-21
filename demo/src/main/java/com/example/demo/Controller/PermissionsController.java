package com.example.demo.Controller;

import com.example.demo.Dto.Permissions.PermissionRequest;
import com.example.demo.Service.PermissionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/permissions")
@Validated
public class PermissionsController {

    private final Logger logger = LoggerFactory.getLogger(PermissionsController.class);

    @Resource
    private PermissionService permissionService;

    @GetMapping("/testLogin")
    public Map<String, Object> testLogin() {
        logger.info("permissions/testLogin: Permissions is working!");
        return Map.of("message", "Permissions is working!");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid
            @RequestBody
            PermissionRequest request) {
        return permissionService.register(request);
    }

    @GetMapping("/query")
    public ResponseEntity<?> query() {
        return permissionService.query();
    }

    @PutMapping("/update")
    public ResponseEntity<?> update(
            @Valid
            @RequestBody
            PermissionRequest request) {
        return permissionService.update(request);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(
            @RequestParam
            String username,
            String password) {
        return permissionService.delete(username, password);
    }

}
