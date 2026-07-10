package com.example.demo.Service.UserCar;

import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface UserServiceCar {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> productsCarSelect(@Valid QueryCarItemRequest request);

    ResponseEntity<?> createCarItem(@Valid CreateCarItemRequest request);

    ResponseEntity<?> queryCarItem(@Valid QueryCarItemRequest request);

    ResponseEntity<?> updateCarItem(@Valid UpdateCarItemRequest request);

    ResponseEntity<?> deleteCarItem(@Valid DeleteCarItemRequest request);

    ResponseEntity<?> confirmItem(@Valid ConfirmItemRequest request);

}
