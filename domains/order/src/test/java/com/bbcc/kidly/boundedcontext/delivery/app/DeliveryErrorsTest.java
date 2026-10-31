package com.bbcc.kidly.boundedcontext.delivery.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.delivery.domain.DeliveryRuleViolationException;
import com.bbcc.kidly.boundedcontext.delivery.domain.DeliveryRuleViolationException.Rule;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DeliveryErrorsTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Rule.class)
    @DisplayName("규칙 위반은 같은 이름의 DeliveryErrorCode와 그 기본 설명으로 바뀐다(응답 상태 · title · detail 그대로)")
    void mapsEveryRuleToSameNamedCode(Rule rule) {
        BusinessException converted = DeliveryErrors.toBusiness(new DeliveryRuleViolationException(rule));

        DeliveryErrorCode expected = DeliveryErrorCode.valueOf(rule.name());
        assertThat(converted.getErrorCode()).isEqualTo(expected);
        assertThat(converted.getDetail()).isEqualTo(expected.detail());
    }
}
