package com.bbcc.kidly.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InitialDataTest {

    @InitialData
    static class InitialDataBean {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(InitialDataBean.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "prod,dev"})
    @DisplayName("local과 dev 서버에서는 초기 데이터 빈이 등록된다")
    void registered(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
            .run(context -> assertThat(context).hasSingleBean(InitialDataBean.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "prod"})
    @DisplayName("test와 운영에서는 초기 데이터 빈이 등록되지 않는다")
    void notRegistered(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
            .run(context -> assertThat(context).doesNotHaveBean(InitialDataBean.class));
    }
}
