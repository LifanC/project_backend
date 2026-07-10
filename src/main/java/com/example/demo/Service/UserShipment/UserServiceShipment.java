package com.example.demo.Service.UserShipment;

import com.example.demo.Dto.User.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface UserServiceShipment {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> userShipments(@Valid ShipmentsRequest request);

}
