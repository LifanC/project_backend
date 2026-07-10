package com.example.demo.Config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi productApi() {
        return GroupedOpenApi.builder()
                .group("1-product")
                .pathsToMatch("/v1/products/**")
                .pathsToExclude("/v1/products/uploadFile/**")
                .build();
    }

    @Bean
    public GroupedOpenApi permissionsApi() {
        return GroupedOpenApi.builder()
                .group("2-permissions")
                .pathsToMatch("/v1/permissions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi loginApi() {
        return GroupedOpenApi.builder()
                .group("3-login")
                .pathsToMatch("/v1/login/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("4.1-userCar")
                .pathsToMatch("/v1/user/car/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userQuotationApi() {
        return GroupedOpenApi.builder()
                .group("4.2-userQuotation")
                .pathsToMatch("/v1/user/quotation/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userShipmentApi() {
        return GroupedOpenApi.builder()
                .group("4.3-userShipment")
                .pathsToMatch("/v1/user/shipment/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userPaymentApi() {
        return GroupedOpenApi.builder()
                .group("4.4-userPayment")
                .pathsToMatch("/v1/user/payment/**")
                .build();
    }


    @Bean
    public GroupedOpenApi orderbackendApi() {
        return GroupedOpenApi.builder()
                .group("5-orderbackend")
                .pathsToMatch("/v1/orderbackend/*")
                .build();
    }

    @Bean
    public GroupedOpenApi orderbackendQuotationApi() {
        return GroupedOpenApi.builder()
                .group("5.1-orderbackendQuotation")
                .pathsToMatch("/v1/orderbackend/quotation/**")
                .build();
    }

    @Bean
    public GroupedOpenApi orderbackendOrderApi() {
        return GroupedOpenApi.builder()
                .group("5.2-orderbackendOrder")
                .pathsToMatch("/v1/orderbackend/order/**")
                .build();
    }

    @Bean
    public GroupedOpenApi orderbackendShipmentApi() {
        return GroupedOpenApi.builder()
                .group("5.3-orderbackendShipment")
                .pathsToMatch("/v1/orderbackend/shipment/**")
                .build();
    }

    @Bean
    public GroupedOpenApi notificationsApi() {
        return GroupedOpenApi.builder()
                .group("6-notifications")
                .pathsToMatch("/v1/notifications/**")
                .build();
    }

}