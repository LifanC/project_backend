package com.example.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan(basePackages = "com.example.demo.**.Mapper")
@EnableScheduling
@SpringBootApplication
public class DemoApplication {

    // 建立 Logger
    private static final Logger logger = LoggerFactory.getLogger(DemoApplication.class);

	public static void main(String[] args) {

        // 啟動 Spring Boot
        ApplicationContext context = SpringApplication.run(DemoApplication.class, args);

        // 取得 Environment
        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "");
        String contextPath = env.getProperty("server.servlet.context-path", "");

        // 用 Logger 顯示完整 URL
        logger.info("Application is running at: http://localhost:{}{}", port, contextPath);
	}

}
