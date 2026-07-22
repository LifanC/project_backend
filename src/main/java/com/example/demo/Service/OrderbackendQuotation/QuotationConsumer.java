package com.example.demo.Service.OrderbackendQuotation;

import com.example.demo.Dto.Notifications.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
public class QuotationConsumer {

    private final Logger logger = LoggerFactory.getLogger(QuotationConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public QuotationConsumer(
            SimpMessagingTemplate messagingTemplate,
            SimpUserRegistry simpUserRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
    }

    @RabbitListener(queues = "quotation:queue:create")
    public void receiveQuotationQueueCreate(NotificationMessage message) {

        logger.info(
                "目前 WebSocket users={}",
                simpUserRegistry.getUsers()
        );

        logger.info("quotation 收到訊息: {}, {}, {}",
                message.getUsername(),
                message.getTitle(),
                message.getContent()
        );

        // 推送給前端
        messagingTemplate.convertAndSendToUser(
                message.getUsername(),
                "/queue/notifications",
                message
        );

        logger.info(
                "推播完成給 user={}",
                message.getUsername()
        );

    }

}
