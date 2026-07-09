package com.example.demo.Service;

import com.example.demo.Dto.Permissions.PermissionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface PermissionService {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> register(@Valid PermissionRequest request);

    ResponseEntity<?> query();

    ResponseEntity<?> update(@Valid PermissionRequest request);

    ResponseEntity<?> delete(@Valid PermissionRequest request);
}
