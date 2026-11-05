package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.domain.AdminAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.AdminStatus;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminAccountTest {

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 저장한다")
    void normalizesEmail() {
        var account = AdminAccount.create("  Ops@Kidly.KR ", "hash", 1L);

        assertThat(account.getEmail()).isEqualTo("ops@kidly.kr");
        assertThat(AdminAccount.normalizeEmail(" Ops@Kidly.KR\t")).isEqualTo("ops@kidly.kr");
    }

    @Test
    @DisplayName("만들면 ACTIVE이고 만든 관리자 ID를 남긴다")
    void createdActive() {
        var account = AdminAccount.create("ops@kidly.kr", "hash", 7L);

        assertThat(account.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        assertThat(account.isActive()).isTrue();
        assertThat(account.getCreatedBy()).isEqualTo(7L);
    }

    @Test
    @DisplayName("DISABLED면 로그인할 수 없는 상태다")
    void disabledIsNotActive() {
        assertThat(AdminAccountFixture.disabled().isActive()).isFalse();
    }

    @Test
    void disableRecordsActorAndTimeOnlyOnce() {
        var account = AdminAccountFixture.active(2L);
        var now = LocalDateTime.of(2026, 11, 3, 12, 0);

        assertThat(account.disable(1L, now)).isTrue();
        assertThat(account.disable(3L, now.plusHours(1))).isFalse();
        assertThat(account.getStatus()).isEqualTo(AdminStatus.DISABLED);
        assertThat(account.getDisabledBy()).isEqualTo(1L);
        assertThat(account.getDisabledAt()).isEqualTo(now);
    }

    @Test
    void cannotDisableSelf() {
        var account = AdminAccountFixture.active(2L);
        assertThatThrownBy(() -> account.disable(2L, LocalDateTime.now()))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(AuthErrorCode.ADMIN_NOT_DISABLABLE);
    }
}
