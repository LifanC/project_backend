package com.example.demo.Config.Rabbit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {

    private final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @RabbitListener(queues = "order.queue")
    public void receive(String message) {

        logger.info("收到訊息: {}", message);
    }
}
