package com.bbcc.kidly.boundedcontext.order.out.config;

import com.bbcc.kidly.boundedcontext.order.app.port.StuckOrderDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.ai.AiDiagnosisPrompt;
import com.bbcc.kidly.boundedcontext.order.out.ai.DisabledDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.ai.FakeDiagnoser;
import com.bbcc.kidly.boundedcontext.order.out.ai.OpenAiCompatibleDiagnoser;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 멈춘 주문 진단 포트 구현을 kidly.order.ai.provider로 고른다(#926). 꺼졌거나 키가 없으면 꺼진 구현.
 * 서킷브레이커는 빈으로 두지 않고 어댑터 안에서 만든다(#708과 같음). 설정은 common의 @ConfigurationPropertiesScan이 등록한다.
 */
@Slf4j
@Configuration
class OrderAiConfig {

    @Bean
    StuckOrderDiagnoser stuckOrderDiagnoser(OrderAiProperties properties, JsonMapper jsonMapper) {
        if (!properties.active()) {
            log.info("멈춘 주문 AI 진단 꺼짐: enabled={} provider={} keyPresent={}", properties.enabled(),
                properties.provider(), properties.hasUsableKey());
            return new DisabledDiagnoser();
        }
        AiDiagnosisPrompt prompt = new AiDiagnosisPrompt(jsonMapper);
        log.info("멈춘 주문 AI 진단 켜짐: provider={} model={}", properties.provider(), properties.model());
        return switch (properties.provider()) {
            case FAKE -> new FakeDiagnoser(prompt, properties.model());
            case OPENAI_COMPATIBLE -> {
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout(properties.connectTimeout());
                requestFactory.setReadTimeout(properties.timeout());
                yield OpenAiCompatibleDiagnoser.create(properties, RestClient.builder().requestFactory(requestFactory),
                    prompt, jsonMapper, Clock.systemDefaultZone());
            }
        };
    }
}
