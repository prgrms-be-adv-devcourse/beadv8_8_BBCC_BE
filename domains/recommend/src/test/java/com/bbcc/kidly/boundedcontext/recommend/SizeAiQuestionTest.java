package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SizeAiQuestionTest {

    @Test
    @DisplayName("성장 속도는 AI에게 줄 때 cm 값을 소수 둘째 자리에서 반올림하고 아이 기록 비중을 퍼센트 정수로 바꾼다")
    void roundGrowthSpeedForQuestion() {
        GrowthVelocity velocity = new GrowthVelocity(0.125, 0.9049, 0.7, 3, 5.954, 0.905, 0.5, "한국아동패널");

        SizeAiQuestion.GrowthSpeed growthSpeed = SizeAiQuestion.GrowthSpeed.from(velocity);

        assertThat(growthSpeed).isEqualTo(new SizeAiQuestion.GrowthSpeed(new BigDecimal("0.13"),
            new BigDecimal("0.90"), 3, new BigDecimal("5.95"), 91, new BigDecimal("0.50"), "한국아동패널"));
    }

    @Test
    @DisplayName("최근 속도를 계산할 수 없으면 AI에게 주는 최근 속도도 null이다")
    void keepMissingRecentSpeedNull() {
        GrowthVelocity velocity = new GrowthVelocity(0.5, null, null, 0, 0.0, 0.0, 0.5, "한국아동패널");

        assertThat(SizeAiQuestion.GrowthSpeed.from(velocity).recentCmPerMonth()).isNull();
    }
}
