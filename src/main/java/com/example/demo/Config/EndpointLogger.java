package com.example.demo.Config;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class EndpointLogger implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EndpointLogger.class);

    @Override
    public void run(@Nonnull String... args) {
        logger.info("""
                                
                                \tLocal:      http://localhost:8080/api/swagger-ui/index.html
                                \tLocal:      http://localhost:8080/api/v3/api-docs
                                ----------------------------------------------------------"""
        );
    }
}

