package com.example.demo.Service;

import com.example.demo.Dto.Orderbackend.QueryQuotationsItemRequest;
import com.example.demo.Dto.Orderbackend.QueryUserProductItemRequest;
import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface OrderbackendService {
    ResponseEntity<?> testLogin();

    ResponseEntity<?> takeToken(@Valid UserRequest request);

    ResponseEntity<?> validate(@Valid UserTokenValidateRequest request);

    ResponseEntity<?> logout(@Valid QueryUserRequest request);

    ResponseEntity<?> queryUser(@Valid QueryUserRequest request);

    ResponseEntity<?> queryUserProductItem(@Valid QueryUserProductItemRequest request);

    ResponseEntity<?> queryQuotationsItem(@Valid QueryQuotationsItemRequest request);
}
