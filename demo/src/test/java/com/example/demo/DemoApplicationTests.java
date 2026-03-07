package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

    private static final Logger logger = LoggerFactory.getLogger(DemoApplicationTests.class);

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() throws InterruptedException {

        /*
         * thread 建立
         *       ↓
         * 全部 thread ready
         *       ↓
         * startLatch.countDown()
         *       ↓
         * 所有 thread 同時執行
         * */
        int corePoolSize = 1;
        ExecutorService executor = Executors.newFixedThreadPool(corePoolSize);

        // 等待所有 thread 準備好
        CountDownLatch readyLatch = new CountDownLatch(corePoolSize);

        // 控制同時開始
        CountDownLatch startLatch = new CountDownLatch(1);

        // 等待全部完成
        CountDownLatch doneLatch = new CountDownLatch(corePoolSize);

        for (int i = 0; i < corePoolSize; i++) {
            final int index = i + 1;
            executor.execute(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 等待一起開始
                    logger.info("{} {} - start request", index, Thread.currentThread().getName());

                    String permissionsUrl = String.format("http://localhost:%d/api/permissions/testLogin", port);
                    String userUrl = String.format("http://localhost:%d/api/user/testLogin", port);

                    String username = "lukechen";
                    String password = "1qaz@WSX";

                    // Base64 編碼 username:password
                    String auth = username + ":" + password;
                    String encodedAuth = Base64.getEncoder()
                            .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    String authHeader = "Basic " + encodedAuth;

                    // 設定 Header
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", authHeader);
                    headers.setContentType(MediaType.APPLICATION_JSON); // 如果 API 接收 JSON

                    HttpEntity<String> entity = new HttpEntity<>(null, headers);

                    // 呼叫 API
                    RestTemplate restTemplate = new RestTemplate();
                    ResponseEntity<String> permissionsResponse = restTemplate.exchange(
                            permissionsUrl,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

                    // 驗證回應
                    logger.info("permissions Response Status: {}", permissionsResponse.getStatusCode());
                    logger.info("permissions Response Body: {}", permissionsResponse.getBody());

                    // 斷言
                    assert(permissionsResponse.getStatusCode() == HttpStatus.OK);

                    ResponseEntity<String> userResponse = restTemplate.exchange(
                            userUrl,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

                    // 驗證回應
                    logger.info("user Response Status: {}", userResponse.getStatusCode());
                    logger.info("user Response Body: {}", userResponse.getBody());

                    // 斷言
                    assert(userResponse.getStatusCode() == HttpStatus.OK);

                    // 模擬你的業務邏輯
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 等待所有 thread ready
        readyLatch.await();
        long startTime = System.currentTimeMillis();
        logger.info("===== start stress test =====");

        // 同時開始
        startLatch.countDown();

        // 等待全部完成
        doneLatch.await();

        long endTime = System.currentTimeMillis();

        logger.info("===== finish stress test =====");
        logger.info("total time: {} ms", endTime - startTime);

        executor.shutdown();
    }


}
