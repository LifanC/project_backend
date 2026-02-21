package com.example.demo.Config;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

@Component
public class EndpointLogger implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EndpointLogger.class);

    @Resource
    private RequestMappingHandlerMapping handlerMapping;

    @Override
    public void run(@Nonnull String... args) {

        // 指定 Controller 名稱
        List<String> targetControllers = List.of(
            "PermissionsController",
            "UsersController"
        );
        // 指定方法名稱（可為 null 表示全部方法）
        List<String> targetMethods = List.of(
            "testLogin"
        );

        String port = "8080";
        String contextPath = "/api";

        logger.info("""
            
            \t=== All Endpoints ===
            ----------------------------------------------------------"""
        );
        handlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {

            // Controller 類名
            String controllerName = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();
            String methods = String.valueOf(mappingInfo.getMethodsCondition());
            String patterns = String.valueOf(mappingInfo.getPathPatternsCondition());

            // 過濾系統內建 URL
            if (patterns.contains("/error")) {
                return;
            }
            // 只顯示指定 Controller
            if (!targetControllers.contains(controllerName)) {
                return;
            }
            // 如果有指定方法，過濾
            if (!targetMethods.contains(methodName)) {
                return;
            }

            logger.info("""
                    
                    ----------------------------------------------------------
                    \t{} {} : {}#{}""",
                methods,
                patterns,
                controllerName,
                methodName
            );
            String methodsFormat =
                methods.substring(methods.indexOf("[") + 1, methods.indexOf("]"))
                    .toLowerCase();
            if ("get".equals(methodsFormat)) {
                logger.info("""
                        
                        \tLocal:      http://localhost:{}{}
                        ----------------------------------------------------------""",
                    port,
                    contextPath +
                        patterns.substring(
                            patterns.indexOf("[") + 1, patterns.indexOf("]")
                        )
                );
            }
        });
    }
}

