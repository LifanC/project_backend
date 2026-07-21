package com.example.demo.Config.Rabbit;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderConsumer {


    @RabbitListener(queues = "order.queue")
    public void receive(String message) {

        System.out.println(
                "收到訊息: " + message
        );
    }
}
