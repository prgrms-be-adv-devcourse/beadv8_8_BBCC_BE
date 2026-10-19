package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.SellerState;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerStateTest {

    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Test
    @DisplayName("ACTIVE면 판매자로 본다")
    void isActive() {
        assertThat(new SellerState(SellerStatus.ACTIVE, APPROVED_AT).isActive()).isTrue();
        assertThat(new SellerState(SellerStatus.SUSPENDED, APPROVED_AT).isActive()).isFalse();
    }

    @Test
    @DisplayName("다른 복사본보다 먼저 일어난 변경인지 비교한다")
    void isOlderThan() {
        var state = new SellerState(SellerStatus.ACTIVE, APPROVED_AT);

        assertThat(state.isOlderThan(new SellerState(SellerStatus.SUSPENDED, APPROVED_AT.plusMinutes(1)))).isTrue();
        assertThat(state.isOlderThan(new SellerState(SellerStatus.SUSPENDED, APPROVED_AT))).isFalse();
        assertThat(state.isOlderThan(new SellerState(SellerStatus.SUSPENDED, APPROVED_AT.minusMinutes(1))))
            .isFalse();
    }
}
