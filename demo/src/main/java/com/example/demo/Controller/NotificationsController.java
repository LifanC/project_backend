package com.example.demo.Controller;

import com.example.demo.Dto.Notifications.NotificationsRequset;
import com.example.demo.Service.NotificationsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/notifications")
@Validated
public class NotificationsController {

    private final NotificationsService notificationsService;

    public NotificationsController(
            NotificationsService notificationsService){
        this.notificationsService = notificationsService;
    }

    @GetMapping("/testLogin")
    public ResponseEntity<?> testLogin() {
        return notificationsService.testLogin();
    }

    @PostMapping("/unread")
    public ResponseEntity<?> unread(
            @Valid
            @RequestBody
            NotificationsRequset request,
            @RequestHeader("Authorization") String authHeader) {
        request.setAuthHeader(authHeader);
        return notificationsService.unread(request);
    }



}
