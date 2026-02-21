package com.example.demo;

//import com.example.demo.Service.PermissionService;
//import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;

@SpringBootTest
class DemoApplicationTests {

//    private static final Logger logger = LoggerFactory.getLogger(DemoApplicationTests.class);
//
//    @Resource
//    private PermissionService permissionService;

    @Test
    void contextLoads() throws InterruptedException {

//        // 模擬 ? 個線程同時請求
//        int corePoolSize = 10;
//        ScheduledExecutorService executor = Executors.newScheduledThreadPool(corePoolSize);
//
//        // 延遲 5 秒後同時發送 3 個請求，模擬快取過期
//        for (int i = 0; i < corePoolSize; i++) {
//            int finalI = i + 1;
//            Runnable task = () -> {
//                permissionService.query();
//                logger.info("{} {} - cache value: ", finalI, Thread.currentThread().getName());
//            };
//            executor.schedule(task, 5, TimeUnit.SECONDS);
//        }
//
//        executor.shutdown();
//        executor.awaitTermination(10, TimeUnit.SECONDS);
    }


}
