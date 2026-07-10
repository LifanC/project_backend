package com.example.demo.Service.OrderbackendOrder;

import com.example.demo.Dto.Orderbackend.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface OrderbackendServiceOrder {
    ResponseEntity<?> testLogin();

    ResponseEntity<?> ordersUser(@Valid OrdersUserItemRequest request);

    ResponseEntity<?> ordersProduct(@Valid OrdersProductItemRequest request);

    ResponseEntity<?> ordersConfirmed(@Valid OrdersConfirmedCancelledItemRequest request);

    ResponseEntity<?> ordersCancelled(@Valid OrdersConfirmedCancelledItemRequest request);

}
