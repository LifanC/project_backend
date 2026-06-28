package com.example.demo.Service;

import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface UserService {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> productsCarSelect(@Valid QueryCarItemRequest request);

    ResponseEntity<?> createCarItem(@Valid CreateCarItemRequest request);

    ResponseEntity<?> queryCarItem(@Valid QueryCarItemRequest request);

    ResponseEntity<?> updateCarItem(@Valid UpdateCarItemRequest request);

    ResponseEntity<?> deleteCarItem(@Valid DeleteCarItemRequest request);

    ResponseEntity<?> confirmItem(@Valid ConfirmItemRequest request);

    ResponseEntity<?> quotationsProductId(@Valid QuotationsProductIdRequest request);

    ResponseEntity<?> quotationsProduct(@Valid QuotationsProductRequest request);

    ResponseEntity<?> userAccepted(@Valid QuotationsProductRequest request);

    ResponseEntity<?> userRejected(@Valid QuotationsProductRequest request);

    ResponseEntity<?> userShipments(@Valid ShipmentsRequest request);

    ResponseEntity<?> userPayments(@Valid PaymentsRequest request);

    ResponseEntity<?> userPayMoney(@Valid PayMoneyRequest request);
}
