package com.example.demo.Service.OrderbackendQuotation;

import com.example.demo.Dto.Orderbackend.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface OrderbackendServiceQuotation {
    ResponseEntity<?> testLogin();

    ResponseEntity<?> quotationsProductItem(@Valid QuotationsProductItemRequest request);

    ResponseEntity<?> confirmQuotationsProductItem(@Valid QuotationsProductItemRequest request);

    ResponseEntity<?> deleteQuotationsProduct(@Valid DeleteQuotationsProductItemRequest request);

    ResponseEntity<?> queryQuotationsProduct(@Valid QueryQuotationsProductItemRequest request);

    ResponseEntity<?> sendQuotationsProduct(@Valid SendQuotationsProductItemRequest request);

}
