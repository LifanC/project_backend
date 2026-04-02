package com.example.demo.Service;

import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface UserService {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> takeToken(@Valid UserRequest request);

    ResponseEntity<?> validate(@Valid UserTokenValidateRequest request);

    ResponseEntity<?> logout(@Valid QueryUserRequest request);

    ResponseEntity<?> productsCarSelect(@Valid QueryCarItemRequest request);

    ResponseEntity<?> createCarItem(@Valid CreateCarItemRequest request);

    ResponseEntity<?> queryCarItem(@Valid QueryCarItemRequest request);

    ResponseEntity<?> updateCarItem(@Valid UpdateCarItemRequest request);

    ResponseEntity<?> deleteCarItem(@Valid DeleteCarItemRequest request);

    ResponseEntity<?> historyCarItem(@Valid QueryCarItemRequest request);

}
