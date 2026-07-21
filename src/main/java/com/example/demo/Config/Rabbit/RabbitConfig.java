package com.example.demo.Config.Rabbit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {


    @Bean
    public Queue orderQueue() {
        return new Queue("order.queue");
    }


    @Bean
    public DirectExchange exchange() {
        return new DirectExchange("order.exchange");
    }


    @Bean
    public Binding binding(
            Queue orderQueue,
            DirectExchange exchange) {

        return BindingBuilder
                .bind(orderQueue)
                .to(exchange)
                .with("order.created");
    }
}
