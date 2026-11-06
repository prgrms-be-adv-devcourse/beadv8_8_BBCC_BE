package com.bbcc.kidly.boundedcontext.order.domain.replica;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 자녀 계정 복사본(#906): 삭제는 한 방향(있음 → 삭제), 생성 · 변경은 늦은 메시지만, 시각은 마이크로초로 잘라 저장 · 비교한다.
 */
class ReplicaChildAccountTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 5, 21, 0);

    @Test
    @DisplayName("생성 메시지로 만들면 프로필 · 보호자를 갖고 삭제되지 않았다")
    void create() {
        ReplicaChildAccount account = ReplicaChildAccount.create(values(false, AT));

        assertThat(account.getAccountId()).isEqualTo(10L);
        assertThat(account.getChildProfileId()).isEqualTo(3L);
        assertThat(account.getGuardianId()).isEqualTo(1L);
        assertThat(account.isDeleted()).isFalse();
        assertThat(account.getEventOccurredAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("삭제는 시각과 상관없이 한 번 반영되고(더 이른 삭제 메시지도), 그 뒤 메시지는 모두 무시한다")
    void deleteOneWay() {
        ReplicaChildAccount account = ReplicaChildAccount.create(values(false, AT));

        assertThat(account.applyIfNewer(values(true, AT.minusMinutes(5)))).isTrue();
        assertThat(account.isDeleted()).isTrue();
        assertThat(account.getDeletedAt()).isEqualTo(AT.minusMinutes(5));

        assertThat(account.applyIfNewer(values(false, AT.plusMinutes(5)))).as("삭제 뒤 생성").isFalse();
        assertThat(account.applyIfNewer(values(true, AT.plusMinutes(5)))).as("다시 삭제").isFalse();
        assertThat(account.isDeleted()).isTrue();
        assertThat(account.getDeletedAt()).isEqualTo(AT.minusMinutes(5));
    }

    @Test
    @DisplayName("삭제 메시지가 먼저 오면 삭제된 행으로 만들고, 뒤에 온 예전 생성 메시지는 무시한다")
    void deleteBeforeCreate() {
        ReplicaChildAccount account = ReplicaChildAccount.create(values(true, AT));

        assertThat(account.isDeleted()).isTrue();
        assertThat(account.applyIfNewer(values(false, AT.minusMinutes(1)))).isFalse();
        assertThat(account.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제되지 않은 계정은 저장된 시각보다 늦은 생성 메시지만 반영한다(같은 시각 · 이른 시각 · 같은 메시지 다시 = 무시)")
    void createOnlyIfNewer() {
        ReplicaChildAccount account = ReplicaChildAccount.create(values(false, AT));

        assertThat(account.applyIfNewer(values(false, AT))).isFalse();
        assertThat(account.applyIfNewer(values(false, AT.minusSeconds(1)))).isFalse();
        assertThat(account.applyIfNewer(new ReplicaChildAccount.Values(10L, 3L, 2L, false, AT.plusSeconds(1))))
            .isTrue();
        assertThat(account.getGuardianId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("시각은 마이크로초로 잘라 저장 · 비교한다(나노초만 다른 같은 메시지는 늦은 것이 아니다)")
    void microsecondPrecision() {
        ReplicaChildAccount account = ReplicaChildAccount.create(values(false, AT.plusNanos(1_000_400)));

        assertThat(account.getEventOccurredAt()).isEqualTo(AT.plusNanos(1_000_000));
        assertThat(account.applyIfNewer(values(false, AT.plusNanos(1_000_900)))).isFalse();
    }

    private static ReplicaChildAccount.Values values(boolean deleted, LocalDateTime occurredAt) {
        return new ReplicaChildAccount.Values(10L, 3L, 1L, deleted, occurredAt);
    }
}
