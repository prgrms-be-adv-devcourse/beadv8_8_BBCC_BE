package com.bbcc.kidly.gateway.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 모든 통합 테스트가 쓰는 가짜 백엔드. kidly-app 라우트(kidly.gateway.app-uri)와
 * 검색 라우트(kidly.gateway.search-uri)가 각각 다른 백엔드로 가게 한다.
 * 그냥 주입하면 kidly-app 백엔드이고, 검색 백엔드는 {@code @Qualifier(SEARCH)}로 받는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingBackendConfiguration {

    public static final String SEARCH = "searchBackend";

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

    @Bean
    DynamicPropertyRegistrar recordingBackendUris(RecordingBackend appBackend,
        @Qualifier(SEARCH) RecordingBackend searchBackend) {
        return registry -> {
            registry.add("kidly.gateway.app-uri", () -> "http://127.0.0.1:" + appBackend.port());
            registry.add("kidly.gateway.search-uri", () -> "http://127.0.0.1:" + searchBackend.port());
        };
    }
}
