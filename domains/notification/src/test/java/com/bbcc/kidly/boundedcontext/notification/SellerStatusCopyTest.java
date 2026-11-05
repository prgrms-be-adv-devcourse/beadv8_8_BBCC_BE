package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.domain.SellerStatusCopy;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerStatusCopyTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Test
    @DisplayName("더 늦게 발생한 이벤트로 상태가 바뀌면 덮어쓰고 바뀌었다고 알린다")
    void changeNewer() {
        var copy = SellerStatusCopy.create(1L, SellerStatus.ACTIVE, OCCURRED_AT);

        var changed = copy.changeIfNewer(SellerStatus.SUSPENDED, OCCURRED_AT.plusSeconds(1));

        assertThat(changed).isTrue();
        assertThat(copy.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(copy.getSourceOccurredAt()).isEqualTo(OCCURRED_AT.plusSeconds(1));
    }

    @Test
    @DisplayName("상태가 같으면(상호명만 바뀐 경우) 발생 시각만 덮어쓰고 바뀌지 않았다고 알린다")
    void sameStatus() {
        var copy = SellerStatusCopy.create(1L, SellerStatus.ACTIVE, OCCURRED_AT);

        var changed = copy.changeIfNewer(SellerStatus.ACTIVE, OCCURRED_AT.plusSeconds(1));

        assertThat(changed).isFalse();
        assertThat(copy.getSourceOccurredAt()).isEqualTo(OCCURRED_AT.plusSeconds(1));
    }

    @Test
    @DisplayName("더 일찍 발생한 옛 이벤트는 무시한다")
    void ignoreOlder() {
        var copy = SellerStatusCopy.create(1L, SellerStatus.SUSPENDED, OCCURRED_AT);

        var changed = copy.changeIfNewer(SellerStatus.ACTIVE, OCCURRED_AT.minusSeconds(1));

        assertThat(changed).isFalse();
        assertThat(copy.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(copy.getSourceOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("발생 시각은 마이크로초로 잘라, 같은 메시지를 다시 받으면 늦은 이벤트로 보지 않는다")
    void sameMessageWithNanos() {
        var occurredAt = OCCURRED_AT.plusNanos(123_456_789);
        var copy = SellerStatusCopy.create(1L, SellerStatus.ACTIVE, occurredAt);

        assertThat(copy.changeIfNewer(SellerStatus.SUSPENDED, occurredAt)).isFalse();
        assertThat(copy.getStatus()).isEqualTo(SellerStatus.ACTIVE);
    }
}
