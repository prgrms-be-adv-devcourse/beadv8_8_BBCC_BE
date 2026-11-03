package com.bbcc.kidly.gateway.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 모든 통합 테스트가 쓰는 가짜 백엔드. kidly-app 라우트(kidly.gateway.app-uri), 검색 라우트(kidly.gateway.search-uri),
 * 자녀 프로필 라우트(kidly.gateway.childprofile-uri), 추천 라우트(kidly.gateway.recommend-uri), 결제 라우트(kidly.gateway.payment-uri)가
 * 각각 다른 백엔드로 가게 한다. 그냥 주입하면 kidly-app 백엔드이고, 검색 백엔드는 {@code @Qualifier(SEARCH)},
 * 자녀 프로필 백엔드는 {@code @Qualifier(CHILDPROFILE)}, 추천 백엔드는 {@code @Qualifier(RECOMMEND)},
 * 결제 백엔드는 {@code @Qualifier(PAYMENT)}로 받는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingBackendConfiguration {

    public static final String SEARCH = "searchBackend";
    public static final String CHILDPROFILE = "childProfileBackend";
    public static final String RECOMMEND = "recommendBackend";
    public static final String PAYMENT = "paymentBackend";

    // 컨텍스트가 닫힐 때 close()로 멈춘다
    @Bean
    @Primary
    RecordingBackend recordingBackend() {
        return RecordingBackend.start();
    }

    @Bean(SEARCH)
    RecordingBackend searchBackend() {
        return RecordingBackend.start();
    }

    @Bean(CHILDPROFILE)
    RecordingBackend childProfileBackend() {
        return RecordingBackend.start();
    }

    @Bean(RECOMMEND)
    RecordingBackend recommendBackend() {
        return RecordingBackend.start();
    }

    @Bean(PAYMENT)
    RecordingBackend paymentBackend() {
        return RecordingBackend.start();
    }

    @Bean
    DynamicPropertyRegistrar recordingBackendUris(RecordingBackend appBackend,
        @Qualifier(SEARCH) RecordingBackend searchBackend,
        @Qualifier(CHILDPROFILE) RecordingBackend childProfileBackend,
        @Qualifier(RECOMMEND) RecordingBackend recommendBackend,
        @Qualifier(PAYMENT) RecordingBackend paymentBackend) {
        return registry -> {
            registry.add("kidly.gateway.app-uri", () -> "http://127.0.0.1:" + appBackend.port());
            registry.add("kidly.gateway.search-uri", () -> "http://127.0.0.1:" + searchBackend.port());
            registry.add("kidly.gateway.childprofile-uri", () -> "http://127.0.0.1:" + childProfileBackend.port());
            registry.add("kidly.gateway.recommend-uri", () -> "http://127.0.0.1:" + recommendBackend.port());
            registry.add("kidly.gateway.payment-uri", () -> "http://127.0.0.1:" + paymentBackend.port());
        };
    }
}
