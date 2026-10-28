package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.out.toss.TossProperties;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TossPropertiesIntegrationTest {

    @Autowired
    private TossProperties properties;

    @Test
    @DisplayName("결제 설정 파일의 토스 설정을 읽는다")
    void bindsTossSettings() {
        assertThat(properties.baseUrl()).isEqualTo("https://api.tosspayments.com");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
