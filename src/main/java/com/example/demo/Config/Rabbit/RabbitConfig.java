package com.example.demo.Config.Rabbit;

import com.example.demo.Common.RabbitKey;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Queue quotationQueueCreate() {
        String quotationQueueCreateName = String.format(RabbitKey.rabbitKey.get("quotation_create"), "queue");
        return new Queue(quotationQueueCreateName);
    }

    @Bean
    public DirectExchange quotationExchangeCreate() {
        String quotationDirectExchangeCreateName = String.format(RabbitKey.rabbitKey.get("quotation_create"), "exchange");
        return new DirectExchange(quotationDirectExchangeCreateName);
    }

    @Bean
    public Binding orderBindingCreate(
            Queue quotationQueueCreate,
            DirectExchange quotationExchangeCreate) {
        String quotationBindingCreateName = String.format(RabbitKey.rabbitKey.get("quotation_create"), "binding");
        return BindingBuilder
                .bind(quotationQueueCreate)
                .to(quotationExchangeCreate)
                .with(quotationBindingCreateName);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(
                messageConverter
        );
        return template;
    }


}
