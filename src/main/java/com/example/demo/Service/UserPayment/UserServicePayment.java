package com.example.demo.Service.UserPayment;

import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface UserServicePayment {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> userPayments(@Valid PaymentsRequest request);

    ResponseEntity<?> userPayMoney(@Valid PayMoneyRequest request);
}
