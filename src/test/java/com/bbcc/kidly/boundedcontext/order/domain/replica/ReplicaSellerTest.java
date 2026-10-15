package com.bbcc.kidly.boundedcontext.order.domain.replica;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplicaSellerTest {

    @Test
    @DisplayName("이벤트 값으로 판매자 복사본을 만든다(상태는 이름 문자열)")
    void create() {
        ReplicaSeller replica = ReplicaSeller.create(seller("키들리샵", "ACTIVE", OCCURRED_AT));

        assertThat(replica.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(replica.getBusinessName()).isEqualTo("키들리샵");
        assertThat(replica.getStatus()).isEqualTo("ACTIVE");
        assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("저장된 시각보다 1초 늦은 이벤트면 상호명 · 상태 · 시각을 덮어쓴다")
    void appliesNewerEvent() {
        ReplicaSeller replica = ReplicaSeller.create(seller("키들리샵", "ACTIVE", OCCURRED_AT));

        boolean applied = replica.applyIfNewer(seller("키들리 아동복", "SUSPENDED", OCCURRED_AT.plusSeconds(1)));

        assertThat(applied).isTrue();
        assertThat(replica.getBusinessName()).isEqualTo("키들리 아동복");
        assertThat(replica.getStatus()).isEqualTo("SUSPENDED");
        assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT.plusSeconds(1));
    }

    @Test
    @DisplayName("저장된 시각과 같거나 1초 이른 이벤트는 무시한다(중복 · 순서 뒤바뀜)")
    void ignoresSameOrOlderEvent() {
        ReplicaSeller replica = ReplicaSeller.create(seller("키들리샵", "ACTIVE", OCCURRED_AT));

        assertThat(replica.applyIfNewer(seller("같은 시각", "SUSPENDED", OCCURRED_AT))).isFalse();
        assertThat(replica.applyIfNewer(seller("이른 시각", "SUSPENDED", OCCURRED_AT.minusSeconds(1)))).isFalse();

        assertThat(replica.getBusinessName()).isEqualTo("키들리샵");
        assertThat(replica.getStatus()).isEqualTo("ACTIVE");
        assertThat(replica.getEventOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    private static ReplicaSeller.Values seller(String businessName, String status, LocalDateTime occurredAt) {
        return new ReplicaSeller.Values(SELLER_ID, businessName, status, occurredAt);
    }
}
