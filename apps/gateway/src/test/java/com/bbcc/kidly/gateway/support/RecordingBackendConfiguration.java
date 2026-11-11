package com.bbcc.kidly.gateway.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** 서비스별 가짜 백엔드와 라우트 URI를 준비한다. */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingBackendConfiguration {

    public static final String MEMBER = "memberBackend";
    public static final String SEARCH = "searchBackend";
    public static final String RECOMMEND = "recommendBackend";
    public static final String SETTLEMENT = "settlementBackend";
    public static final String PAYMENT = "paymentBackend";
    public static final String PRODUCT = "productBackend";
    public static final String ORDER = "orderBackend";
    public static final String COMMUNITY = "communityBackend";
    public static final String NOTIFICATION = "notificationBackend";
    public static final String CHATBOT = "chatbotBackend";

    // 컨텍스트가 닫힐 때 close()로 멈춘다
    @Bean(MEMBER)
    RecordingBackend memberBackend() {
        return RecordingBackend.start();
    }

    @Bean(SEARCH)
    RecordingBackend searchBackend() {
        return RecordingBackend.start();
    }

    @Bean(RECOMMEND)
    RecordingBackend recommendBackend() {
        return RecordingBackend.start();
    }

    @Bean(SETTLEMENT)
    RecordingBackend settlementBackend() {
        return RecordingBackend.start();
    }

    @Bean(PAYMENT)
    RecordingBackend paymentBackend() {
        return RecordingBackend.start();
    }

    @Bean(PRODUCT)
    RecordingBackend productBackend() {
        return RecordingBackend.start();
    }

    @Bean(ORDER)
    RecordingBackend orderBackend() {
        return RecordingBackend.start();
    }

    @Bean(COMMUNITY)
    RecordingBackend communityBackend() {
        return RecordingBackend.start();
    }

    @Bean(NOTIFICATION)
    RecordingBackend notificationBackend() {
        return RecordingBackend.start();
    }

    @Bean(CHATBOT)
    RecordingBackend chatbotBackend() {
        return RecordingBackend.start();
    }

    @Bean
    DynamicPropertyRegistrar recordingBackendUris(@Qualifier(MEMBER) RecordingBackend memberBackend,
        @Qualifier(SEARCH) RecordingBackend searchBackend,
        @Qualifier(RECOMMEND) RecordingBackend recommendBackend,
        @Qualifier(SETTLEMENT) RecordingBackend settlementBackend,
        @Qualifier(PAYMENT) RecordingBackend paymentBackend,
        @Qualifier(PRODUCT) RecordingBackend productBackend,
        @Qualifier(ORDER) RecordingBackend orderBackend,
        @Qualifier(COMMUNITY) RecordingBackend communityBackend,
        @Qualifier(NOTIFICATION) RecordingBackend notificationBackend,
        @Qualifier(CHATBOT) RecordingBackend chatbotBackend) {
        return registry -> {
            registry.add("kidly.gateway.member-uri", () -> "http://127.0.0.1:" + memberBackend.port());
            registry.add("kidly.gateway.search-uri", () -> "http://127.0.0.1:" + searchBackend.port());
            registry.add("kidly.gateway.recommend-uri", () -> "http://127.0.0.1:" + recommendBackend.port());
            registry.add("kidly.gateway.settlement-uri", () -> "http://127.0.0.1:" + settlementBackend.port());
            registry.add("kidly.gateway.payment-uri", () -> "http://127.0.0.1:" + paymentBackend.port());
            registry.add("kidly.gateway.product-uri", () -> "http://127.0.0.1:" + productBackend.port());
            registry.add("kidly.gateway.order-uri", () -> "http://127.0.0.1:" + orderBackend.port());
            registry.add("kidly.gateway.community-uri", () -> "http://127.0.0.1:" + communityBackend.port());
            registry.add("kidly.gateway.notification-uri", () -> "http://127.0.0.1:" + notificationBackend.port());
            registry.add("kidly.gateway.chatbot-uri", () -> "http://127.0.0.1:" + chatbotBackend.port());
        };
    }
}
