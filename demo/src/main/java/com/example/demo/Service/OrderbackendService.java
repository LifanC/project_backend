package com.example.demo.Service;

import com.example.demo.Dto.Orderbackend.DeleteQuotationsProductItemRequest;
import com.example.demo.Dto.Orderbackend.QueryQuotationsProductItemRequest;
import com.example.demo.Dto.Orderbackend.QuotationsProductItemRequest;
import com.example.demo.Dto.Orderbackend.SendQuotationsProductItemRequest;
import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface OrderbackendService {
    ResponseEntity<?> testLogin();

    ResponseEntity<?> takeToken(@Valid UserRequest request);

    ResponseEntity<?> validate(@Valid UserTokenValidateRequest request);

    ResponseEntity<?> logout(@Valid QueryUserRequest request);

    ResponseEntity<?> queryUser(@Valid QueryUserRequest request);

    ResponseEntity<?> quotationsProductItem(@Valid QuotationsProductItemRequest request);

    ResponseEntity<?> confirmQuotationsProductItem(@Valid QuotationsProductItemRequest request);

    ResponseEntity<?> deleteQuotationsProduct(@Valid DeleteQuotationsProductItemRequest request);

    ResponseEntity<?> queryQuotationsProduct(@Valid QueryQuotationsProductItemRequest request);

    ResponseEntity<?> sendQuotationsProduct(@Valid SendQuotationsProductItemRequest request);
}
