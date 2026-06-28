package com.example.demo.Controller;

import com.example.demo.Dto.Permissions.PermissionRequest;
import com.example.demo.Service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Permission API", description = "註冊使用者相關功能")
@RestController
@RequestMapping("/v1/permissions")
@Validated
public class PermissionsController {

    private final PermissionService permissionService;

    public PermissionsController(
            PermissionService permissionService){
        this.permissionService = permissionService;
    }

    @Operation(summary = "1.測試登入", description = "驗證 API 是否正常運作")
    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return permissionService.testLogin();
    }

    @Operation(summary = "2.註冊使用著", description = "註冊使用者資料")
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid
            @RequestBody
            PermissionRequest request) {
        return permissionService.register(request);
    }

    @Operation(summary = "3.查詢使用著", description = "查詢使用著資料")
    @GetMapping("/query")
    public ResponseEntity<?> query() {
        return permissionService.query();
    }

    @Operation(summary = "4.更新使用著", description = "更新使用著資料")
    @PutMapping("/update")
    public ResponseEntity<?> update(
            @Valid
            @RequestBody
            PermissionRequest request) {
        return permissionService.update(request);
    }

    @Operation(summary = "5.刪除使用著", description = "刪除使用著資料")
    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(
            @RequestParam
            @Schema(description = "刪除使用者帳號未輸入", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "刪除帳號未輸入")
            String username,
            @Schema(description = "刪除使用者密碼未輸入", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "刪除密碼未輸入")
            String password) {
        return permissionService.delete(username, password);
    }

}
