package com.example.demo.Service.UserQuotation;

import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface UserServiceQuotation {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> quotationsProductId(@Valid QuotationsProductIdRequest request);

    ResponseEntity<?> quotationsProduct(@Valid QuotationsProductRequest request);

    ResponseEntity<?> userAccepted(@Valid QuotationsProductRequest request);

    ResponseEntity<?> userRejected(@Valid QuotationsProductRequest request);

}
