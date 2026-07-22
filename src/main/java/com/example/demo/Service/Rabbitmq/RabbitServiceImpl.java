package com.example.demo.Service.Rabbitmq;

import com.example.demo.Common.RabbitKey;
import com.example.demo.Dto.Notifications.NotificationMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitServiceImpl implements RabbitService {

    private final RabbitTemplate rabbitTemplate;

    public RabbitServiceImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void quotationCreate(NotificationMessage message) {
        // quotation:exchange:create
        String quotationDirectExchangeCreateName = String.format(RabbitKey.rabbitKey.get("quotation_create"), "exchange");
        // quotation:binding:create
        String quotationBindingCreateName = String.format(RabbitKey.rabbitKey.get("quotation_create"), "binding");
        rabbitTemplate.convertAndSend(
                quotationDirectExchangeCreateName,
                quotationBindingCreateName,
                message
        );
    }


}
