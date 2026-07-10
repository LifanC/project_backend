package com.example.demo.Service.OrderbackendShipment;

import com.example.demo.Dto.Orderbackend.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface OrderbackendServiceShipment {
    ResponseEntity<?> testLogin();

    ResponseEntity<?> shipmentsTrackingNumber(@Valid ShipmentsItemRequest request);

    ResponseEntity<?> shipmentsShipped(@Valid ShipmentsTrackingNumberItemRequest request);

    ResponseEntity<?> shipmentsDelivered(@Valid ShipmentsTrackingNumberItemRequest request);

}
