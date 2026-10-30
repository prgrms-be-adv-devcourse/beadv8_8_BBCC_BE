package com.bbcc.kidly.gateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 모든 통합 테스트가 쓰는 가짜 백엔드. kidly-app 라우트(kidly.gateway.app-uri)가 이 백엔드로 가게 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingBackendConfiguration {

    // 컨텍스트가 닫힐 때 close()로 멈춘다
    @Bean
    RecordingBackend recordingBackend() {
        return RecordingBackend.start();
    }

    @Bean
    DynamicPropertyRegistrar recordingBackendAppUri(RecordingBackend backend) {
        return registry -> registry.add("kidly.gateway.app-uri", () -> "http://127.0.0.1:" + backend.port());
    }
}
