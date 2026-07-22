package com.example.demo.Service.Rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitServiceImpl implements RabbitService {

    private final Logger logger = LoggerFactory.getLogger(RabbitServiceImpl.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitServiceImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(String message) {

        rabbitTemplate.convertAndSend(
                "order.exchange",
                "order.created",
                message
        );
    }


}
