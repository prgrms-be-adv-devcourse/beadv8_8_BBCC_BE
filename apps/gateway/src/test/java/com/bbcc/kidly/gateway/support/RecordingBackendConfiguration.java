package com.bbcc.kidly.gateway.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 모든 통합 테스트가 쓰는 가짜 백엔드. kidly-app 라우트(kidly.gateway.app-uri), 검색 라우트(kidly.gateway.search-uri),
 * 추천 라우트(kidly.gateway.recommend-uri),
 * 정산 라우트(kidly.gateway.settlement-uri), 회원 라우트(kidly.gateway.member-uri), 결제 라우트(kidly.gateway.payment-uri),
 * 상품 라우트(kidly.gateway.product-uri), 주문 라우트(kidly.gateway.order-uri), 커뮤니티 라우트(kidly.gateway.community-uri)가
 * 각각 다른 백엔드로 가게 한다.
 * 그냥 주입하면 kidly-app 백엔드이고, 검색 백엔드는 {@code @Qualifier(SEARCH)},
 * 추천 백엔드는 {@code @Qualifier(RECOMMEND)}, 정산 백엔드는 {@code @Qualifier(SETTLEMENT)},
 * 회원 백엔드는 {@code @Qualifier(MEMBER)}, 결제 백엔드는 {@code @Qualifier(PAYMENT)},
 * 상품 백엔드는 {@code @Qualifier(PRODUCT)}, 주문 백엔드는 {@code @Qualifier(ORDER)},
 * 커뮤니티 백엔드는 {@code @Qualifier(COMMUNITY)}로 받는다.
 */
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

    // 컨텍스트가 닫힐 때 close()로 멈춘다
    @Bean
    @Primary
    RecordingBackend recordingBackend() {
        return RecordingBackend.start();
    }

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

    @Bean
    DynamicPropertyRegistrar recordingBackendUris(RecordingBackend appBackend,
        @Qualifier(MEMBER) RecordingBackend memberBackend,
        @Qualifier(SEARCH) RecordingBackend searchBackend,
        @Qualifier(RECOMMEND) RecordingBackend recommendBackend,
        @Qualifier(SETTLEMENT) RecordingBackend settlementBackend,
        @Qualifier(PAYMENT) RecordingBackend paymentBackend,
        @Qualifier(PRODUCT) RecordingBackend productBackend,
        @Qualifier(ORDER) RecordingBackend orderBackend,
        @Qualifier(COMMUNITY) RecordingBackend communityBackend) {
        return registry -> {
            registry.add("kidly.gateway.member-uri", () -> "http://127.0.0.1:" + memberBackend.port());
            registry.add("kidly.gateway.app-uri", () -> "http://127.0.0.1:" + appBackend.port());
            registry.add("kidly.gateway.search-uri", () -> "http://127.0.0.1:" + searchBackend.port());
            registry.add("kidly.gateway.recommend-uri", () -> "http://127.0.0.1:" + recommendBackend.port());
            registry.add("kidly.gateway.settlement-uri", () -> "http://127.0.0.1:" + settlementBackend.port());
            registry.add("kidly.gateway.payment-uri", () -> "http://127.0.0.1:" + paymentBackend.port());
            registry.add("kidly.gateway.product-uri", () -> "http://127.0.0.1:" + productBackend.port());
            registry.add("kidly.gateway.order-uri", () -> "http://127.0.0.1:" + orderBackend.port());
            registry.add("kidly.gateway.community-uri", () -> "http://127.0.0.1:" + communityBackend.port());
        };
    }
}
