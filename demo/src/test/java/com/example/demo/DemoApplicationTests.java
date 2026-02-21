package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;

@SpringBootTest
class DemoApplicationTests {

//    private static final Logger logger = LoggerFactory.getLogger(DemoApplicationTests.class);

    @Test
    void contextLoads() throws InterruptedException {

//        // 模擬 ? 個線程同時請求
//        int corePoolSize = 10;
//        ScheduledExecutorService executor = Executors.newScheduledThreadPool(corePoolSize);
//
//        // 延遲 5 秒後同時發送 3 個請求，模擬
//        for (int i = 0; i < corePoolSize; i++) {
//            final int finalI = i + 1;
//            Runnable task = () -> {
//                logger.info("{} {} - cache value: ", finalI, Thread.currentThread().getName());
//            };
//            executor.schedule(task, 5, TimeUnit.SECONDS);
//        }
//
//        executor.shutdown();
//        executor.awaitTermination(10, TimeUnit.SECONDS);
    }


}
