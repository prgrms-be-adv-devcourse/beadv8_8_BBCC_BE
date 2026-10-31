package com.bbcc.kidly.boundedcontext.cart.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException;
import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException.Rule;
import com.bbcc.kidly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CartErrorsTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Rule.class)
    @DisplayName("규칙 위반은 같은 이름의 CartErrorCode와 그 기본 설명으로 바뀐다(응답 상태 · title · detail 그대로)")
    void mapsEveryRuleToSameNamedCode(Rule rule) {
        BusinessException converted = CartErrors.toBusiness(new CartRuleViolationException(rule));

        CartErrorCode expected = CartErrorCode.valueOf(rule.name());
        assertThat(converted.getErrorCode()).isEqualTo(expected);
        assertThat(converted.getDetail()).isEqualTo(expected.detail());
    }
}
