package com.example.demo.Service;

import com.example.demo.Dto.Orderbackend.*;
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

    ResponseEntity<?> ordersUser(@Valid OrdersUserItemRequest request);

    ResponseEntity<?> ordersProduct(@Valid OrdersProductItemRequest request);

    ResponseEntity<?> ordersConfirmed(@Valid OrdersConfirmedCancelledItemRequest request);

    ResponseEntity<?> ordersCancelled(@Valid OrdersConfirmedCancelledItemRequest request);

    ResponseEntity<?> shipmentsTrackingNumber(@Valid ShipmentsItemRequest request);

    ResponseEntity<?> shipmentsShipped(@Valid ShipmentsTrackingNumberItemRequest request);

    ResponseEntity<?> shipmentsDelivered(@Valid ShipmentsTrackingNumberItemRequest request);

}
