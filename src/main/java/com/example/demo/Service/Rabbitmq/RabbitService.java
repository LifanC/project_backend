package com.example.demo.Service.Rabbitmq;

import com.example.demo.Dto.Notifications.NotificationMessage;

public interface RabbitService {

    void quotationCreate(NotificationMessage message);

}
