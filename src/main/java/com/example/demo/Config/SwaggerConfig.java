package com.example.demo.Config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi productApi() {
        return GroupedOpenApi.builder()
                .group("A-product")
                .pathsToMatch("/v1/products/*")
                .pathsToExclude("/v1/products/uploadFile/*")
                .build();
    }

    @Bean
    public GroupedOpenApi permissionsApi() {
        return GroupedOpenApi.builder()
                .group("B-permissions")
                .pathsToMatch("/v1/permissions/*")
                .build();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("C-user")
                .pathsToMatch("/v1/user/*")
                .build();
    }

    @Bean
    public GroupedOpenApi userCarApi() {
        return GroupedOpenApi.builder()
                .group("D-userCar")
                .pathsToMatch("/v1/user/car/*")
                .build();
    }

    @Bean
    public GroupedOpenApi userQuotationApi() {
        return GroupedOpenApi.builder()
                .group("E-userQuotation")
                .pathsToMatch("/v1/user/quotation/*")
                .build();
    }

    @Bean
    public GroupedOpenApi userShipmentApi() {
        return GroupedOpenApi.builder()
                .group("F-userShipment")
                .pathsToMatch("/v1/user/shipment/*")
                .build();
    }

    @Bean
    public GroupedOpenApi userPaymentApi() {
        return GroupedOpenApi.builder()
                .group("G-userPayment")
                .pathsToMatch("/v1/user/payment/*")
                .build();
    }

    @Bean
    public GroupedOpenApi orderbackendApi() {
        return GroupedOpenApi.builder()
                .group("H-orderbackend")
                .pathsToMatch("/v1/orderbackend/*")
                .build();
    }

    @Bean
    public GroupedOpenApi orderbackendQuotationApi() {
        return GroupedOpenApi.builder()
                .group("I-orderbackendQuotation")
                .pathsToMatch("/v1/orderbackend/quotation/*")
                .build();
    }

    @Bean
    public GroupedOpenApi orderbackendOrderApi() {
        return GroupedOpenApi.builder()
                .group("J-orderbackendOrder")
                .pathsToMatch("/v1/orderbackend/order/*")
                .build();
    }

    @Bean
    public GroupedOpenApi orderbackendShipmentApi() {
        return GroupedOpenApi.builder()
                .group("K-orderbackendShipment")
                .pathsToMatch("/v1/orderbackend/shipment/*")
                .build();
    }

    @Bean
    public GroupedOpenApi notificationsApi() {
        return GroupedOpenApi.builder()
                .group("L-notifications")
                .pathsToMatch("/v1/notifications/*")
                .build();
    }

}