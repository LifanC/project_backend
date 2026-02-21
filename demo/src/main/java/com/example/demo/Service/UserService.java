package com.example.demo.Service;

import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface UserService {

    ResponseEntity<?> takeToken(@Valid UserRequest request);

    ResponseEntity<?> validate(@Valid UserTokenValidateRequest request);

    ResponseEntity<?> logout(@Valid QueryUserRequest request);

    ResponseEntity<?> queryUser(@Valid QueryUserRequest request);

    ResponseEntity<?> createOrderItem(@Valid CreateOrderItemRequest request);

    ResponseEntity<?> queryOrderItem(@Valid QueryOrderItemRequest request);

    ResponseEntity<?> updateOrderItem(@Valid UpdateOrderItemRequest request);

    ResponseEntity<?> deleteOrderItem(@Valid DeleteOrderItemRequest request);

    ResponseEntity<?> historyOrderItem(@Valid QueryOrderItemRequest request);
}
