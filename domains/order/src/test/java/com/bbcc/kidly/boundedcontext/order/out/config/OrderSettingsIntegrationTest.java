package com.bbcc.kidly.boundedcontext.order.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * 앱이 order.yml을 가져오고, 테스트 프로필(application-test.yml)이 스케줄러 주기만 덮어쓰는지 확인한다.
 */
@IntegrationTest
class OrderSettingsIntegrationTest {

    @Autowired
    private Order.Policy orderPolicy;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("정책값은 order.yml 기본값(결제 마감 PT10M · 자동 확정 P15D)을 쓴다")
    void policyComesFromOrderYml() {
        assertThat(orderPolicy).isEqualTo(new Order.Policy(Duration.ofMinutes(10), Duration.ofDays(15)));
    }

    @Test
    @DisplayName("스케줄러 주기는 테스트 프로필 값(P3650D)이 order.yml 기본값을 덮어쓴다")
    void testProfileOverridesSchedulerIntervals() {
        assertThat(environment.getProperty("kidly.order.payment-expiry-interval")).isEqualTo("P3650D");
        assertThat(environment.getProperty("kidly.order.auto-confirm.interval")).isEqualTo("P3650D");
    }
}
