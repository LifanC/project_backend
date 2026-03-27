package com.example.demo.Controller;

import com.example.demo.Dto.Permissions.PermissionRequest;
import com.example.demo.Service.PermissionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/permissions")
@Validated
public class PermissionsController {

    @Resource
    private PermissionService permissionService;

    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return permissionService.testLogin();
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
