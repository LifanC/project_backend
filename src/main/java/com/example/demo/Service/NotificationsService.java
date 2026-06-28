package com.example.demo.Service;

import com.example.demo.Dto.Notifications.NotificationsRequset;
import org.springframework.http.ResponseEntity;

public interface NotificationsService {

    ResponseEntity<?> testLogin();

    ResponseEntity<?> unread(NotificationsRequset request);

}
