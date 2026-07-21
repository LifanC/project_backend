package com.example.demo.Config.Rabbit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {


    private final OrderProducer producer;


    public OrderController(OrderProducer producer) {
        this.producer = producer;
    }


    @GetMapping
    public String createOrder() {

        /*
         * Producer 負責產生訊息，Exchange 負責分流，Queue 負責保存，Consumer 負責處理。
         * RabbitMQ 的核心思想就是：
         * 讓系統透過訊息溝通，而不是直接互相依賴，提升可靠性、擴展性與非同步處理能力。
         *
         *
         *
         * 元件	責任
         * Controller	接收 HTTP Request、回 Response
         * Producer	把工作送進 RabbitMQ
         * Exchange	分派訊息
         * Queue	保存任務
         * Consumer	執行背景工作
         * Service	商業邏輯
         * Model/Repository	資料存取
         * Database	保存資料
         * View	呈現結果
         *
         *
         *                  Client
         *                     |
         *                     v
         *               Controller
         *                     |
         *                     v
         *               Order Service
         *                     |
         *                     v
         *               RabbitMQ Producer
         *                     |
         *                     v
         *               Exchange
         *                     |
         *                     v
         *               Queue
         *                     |
         *                     v
         *               Order Consumer
         *                     |
         *                     v
         *               Order Service
         *                     |
         *                     v
         *               Repository
         *                     |
         *                     v
         *               Database
         *
         * */

        producer.send("TEST");

        return "sent";
    }
}