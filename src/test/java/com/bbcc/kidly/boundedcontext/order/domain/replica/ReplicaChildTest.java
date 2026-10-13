package com.bbcc.kidly.boundedcontext.order.domain.replica;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.child;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplicaChildTest {

    @Test
    @DisplayName("이벤트 값으로 복사본을 만든다")
    void create() {
        ReplicaChild replica = ReplicaChild.create(child("115.0", false, OCCURRED_AT));

        assertThat(replica.getChildId()).isEqualTo(1L);
        assertThat(replica.getMemberId()).isEqualTo(BUYER_ID);
        assertThat(replica.getName()).isNull();
        assertThat(replica.getHeightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
        assertThat(replica.getWeightKg()).isEqualByComparingTo(new BigDecimal("20.0"));
        assertThat(replica.getBodyType()).isEqualTo("NORMAL");
        assertThat(replica.isDeleted()).isFalse();
        assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("저장된 값보다 늦게 발생한 이벤트면 덮어쓴다")
    void appliesNewerEvent() {
        ReplicaChild replica = ReplicaChild.create(child("115.0", false, OCCURRED_AT));

        boolean applied = replica.applyIfNewer(child("118.5", false, OCCURRED_AT.plusDays(1)));

        assertThat(applied).isTrue();
        assertThat(replica.getHeightCm()).isEqualByComparingTo(new BigDecimal("118.5"));
    }

    @Test
    @DisplayName("같거나 이른 시각의 이벤트는 무시한다 (순서 뒤바뀜 · 중복)")
    void ignoresSameOrOlderEvent() {
        ReplicaChild replica = ReplicaChild.create(child("115.0", false, OCCURRED_AT));

        assertThat(replica.applyIfNewer(child("118.5", false, OCCURRED_AT))).isFalse();
        assertThat(replica.applyIfNewer(child("110.0", false, OCCURRED_AT.minusDays(1)))).isFalse();
        assertThat(replica.getHeightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
    }

    @Test
    @DisplayName("삭제 이벤트가 와도 행을 남기고 삭제 표시만 한다")
    void deleteKeepsRow() {
        ReplicaChild replica = ReplicaChild.create(child("115.0", false, OCCURRED_AT));

        replica.applyIfNewer(child("115.0", true, OCCURRED_AT.plusDays(1)));

        assertThat(replica.isDeleted()).isTrue();
        assertThat(replica.getChildId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("보호자 본인의 자녀인지 판단한다")
    void ownedBy() {
        ReplicaChild replica = ReplicaChild.create(child("115.0", false, OCCURRED_AT));

        assertThat(replica.isOwnedBy(BUYER_ID)).isTrue();
        assertThat(replica.isOwnedBy(SELLER_ID)).isFalse();
    }
}
