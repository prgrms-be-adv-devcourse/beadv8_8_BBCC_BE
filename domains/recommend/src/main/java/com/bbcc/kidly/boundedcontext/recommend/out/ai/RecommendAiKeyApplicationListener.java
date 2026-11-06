package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * OpenAI 공통 키가 비어 있으면 Spring AI의 chat · 임베딩 모델 자동 설정을 끈다.
 * 키가 비어 있어도 Spring AI는 인증 없이 부르는 모델 빈을 만들고, 그 빈을 호출하면 401이 난다.
 *
 * <p>로컬의 {@code .env}는 spring-dotenv의 EnvironmentPostProcessor가 가장 마지막 순서로 읽는다.
 * 같은 EnvironmentPostProcessor로 두면 {@code .env}의 키를 보지 못할 수 있으므로,
 * 모든 EnvironmentPostProcessor가 끝난 뒤 도는 환경 준비 이벤트 리스너로 둔다.
 */
@Slf4j
public class RecommendAiKeyApplicationListener
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    static final String PROPERTY_SOURCE_NAME = "recommendAiKeyOff";

    private static final String API_KEY = "spring.ai.openai.api-key";
    private static final String CHAT_MODEL = "spring.ai.model.chat";
    private static final String EMBEDDING_MODEL = "spring.ai.model.embedding";
    private static final String OFF = "none";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        if (StringUtils.hasText(environment.getProperty(API_KEY))) {
            return;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CHAT_MODEL, OFF);
        properties.put(EMBEDDING_MODEL, OFF);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        log.info("OpenAI 키가 없어 chat · 임베딩 모델을 끈다");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
