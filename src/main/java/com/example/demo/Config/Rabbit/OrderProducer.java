package com.example.demo.Config.Rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducer {

    private final RabbitTemplate rabbitTemplate;


    public OrderProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }


    public void send(String message) {

        rabbitTemplate.convertAndSend(
                "order.exchange",
                "order.created",
                message
        );
    }
}
