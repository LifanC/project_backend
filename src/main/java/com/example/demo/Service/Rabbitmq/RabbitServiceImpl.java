package com.example.demo.Service.Rabbitmq;

import com.example.demo.Common.RabbitKey;
import com.example.demo.Dto.Notifications.NotificationMessage;
import com.example.demo.Service.OrderbackendQuotation.QuotationConsumer;
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
    public void quotationCreate(NotificationMessage message) {
        try {
            // quotation:exchange:create
            String quotationDirectExchangeCreateName = String.format(RabbitKey.rabbitKey.get("quotation_create"), "exchange");
            // quotation:binding:create
            String quotationBindingCreateName = String.format(RabbitKey.rabbitKey.get("quotation_create"), "binding");
            rabbitTemplate.convertAndSend(
                    quotationDirectExchangeCreateName,
                    quotationBindingCreateName,
                    message
            );
        } catch (Exception e) {
            logger.error("RabbitMQ quotation notification failed", e);
            // 不要讓通知失敗影響主要交易流程
        }
    }


}
