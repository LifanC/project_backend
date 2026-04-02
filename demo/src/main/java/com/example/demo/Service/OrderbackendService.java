package com.example.demo.Service;

import com.example.demo.Dto.User.QueryQuotationsItemRequest;
import com.example.demo.Dto.User.QueryUserRequest;
import com.example.demo.Dto.User.UserRequest;
import com.example.demo.Dto.User.UserTokenValidateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface OrderbackendService {
    ResponseEntity<?> testLogin();

    ResponseEntity<?> takeToken(@Valid UserRequest request);

    ResponseEntity<?> validate(@Valid UserTokenValidateRequest request);

    ResponseEntity<?> logout(@Valid QueryUserRequest request);

    ResponseEntity<?> queryUser(@Valid QueryUserRequest request);

    ResponseEntity<?> queryQuotationsItem(@Valid QueryQuotationsItemRequest request);
}
