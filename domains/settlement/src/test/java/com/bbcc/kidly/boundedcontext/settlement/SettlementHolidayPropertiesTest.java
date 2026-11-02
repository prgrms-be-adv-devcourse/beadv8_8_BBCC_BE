package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.settlement.out.holiday.SettlementHolidayProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SettlementHolidayPropertiesTest {

    private static final String BASE_URL = "kidly.settlement.holiday.base-url=https://holiday.test";
    private static final String CONNECT = "kidly.settlement.holiday.connect-timeout=PT3S";
    private static final String READ = "kidly.settlement.holiday.read-timeout=PT5S";
    private static final String FAKE_KEY = "fake-service-key";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfig.class);

    // 값은 ;로 나눈 설정 줄이다
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        CONNECT + ";" + READ,
        "kidly.settlement.holiday.base-url=;" + CONNECT + ";" + READ,
        BASE_URL + ";" + READ,
        BASE_URL + ";" + CONNECT,
        BASE_URL + ";kidly.settlement.holiday.connect-timeout=PT0.5S;" + READ,
        BASE_URL + ";" + CONNECT + ";kidly.settlement.holiday.read-timeout=PT0S"})
    @DisplayName("주소 · 시간 제한이 없거나, 시간 제한이 1초 미만이면 기동에 실패한다")
    void failsOnMissingOrInvalidValue(String properties) {
        runner.withPropertyValues(properties.split(";"))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("인증키가 없어도 기동한다")
    void bindsWithoutServiceKey() {
        runner.withPropertyValues(BASE_URL, CONNECT, READ)
            .run(context -> assertThat(context.getBean(SettlementHolidayProperties.class))
                .isEqualTo(new SettlementHolidayProperties(
                    "https://holiday.test", null, Duration.ofSeconds(3), Duration.ofSeconds(5))));
    }

    @Test
    @DisplayName("문자열로 바꿔도 인증키는 드러나지 않는다")
    void hidesServiceKeyInToString() {
        var properties = new SettlementHolidayProperties(
            "https://holiday.test", FAKE_KEY, Duration.ofSeconds(3), Duration.ofSeconds(5));

        assertThat(properties.toString()).doesNotContain(FAKE_KEY);
    }

    @EnableConfigurationProperties(SettlementHolidayProperties.class)
    static class PropertiesConfig {
    }
}
