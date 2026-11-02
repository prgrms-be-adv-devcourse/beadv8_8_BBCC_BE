package com.bbcc.kidly.boundedcontext.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReplicaProductTest {

    // 나노초가 있는 고정 시각. DB(마이크로초, 반올림)에 저장하면 .123456으로 내려가 저장 값보다 "늦은" 값으로 보일 수 있는 경우다
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123);
    // OCCURRED_AT을 마이크로초로 자른 저장 값
    private static final LocalDateTime STORED = LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_000);
    private static final ReplicaProduct.OptionState ON_SALE = new ReplicaProduct.OptionState("ON_SALE", false);
    private static final ReplicaProduct.OptionState ON_SALE_SOLD_OUT = new ReplicaProduct.OptionState("ON_SALE", true);
    private static final ReplicaProduct.OptionState STOPPED = new ReplicaProduct.OptionState("STOPPED", false);

    @ParameterizedTest
    @ValueSource(strings = {"STOPPED", "SALE_PENDING", "NEW_STATUS"})
    @NullSource
    @DisplayName("상품이 판매 중이 아니면(중지 · 판매 전 · 모르는 값 · 없음) 옵션과 상관없이 판매 종료다")
    void notOnSaleIsSaleEnded(String productStatus) {
        assertThat(DisplayStatus.of(productStatus, List.of(ON_SALE))).isEqualTo(DisplayStatus.SALE_ENDED);
    }

    @Test
    @DisplayName("판매 중 상품의 판매 중 옵션이 모두 품절이면 품절이다")
    void allOnSaleOptionsSoldOut() {
        assertThat(DisplayStatus.of("ON_SALE", List.of(ON_SALE_SOLD_OUT, ON_SALE_SOLD_OUT)))
            .isEqualTo(DisplayStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("판매 중 옵션 하나라도 재고가 있으면 판매 중이다(일부 품절)")
    void someOnSaleOptionInStock() {
        assertThat(DisplayStatus.of("ON_SALE", List.of(ON_SALE_SOLD_OUT, ON_SALE))).isEqualTo(DisplayStatus.ON_SALE);
    }

    @Test
    @DisplayName("판매 중지 옵션은 재고가 있어도 보지 않는다: 남은 판매 중 옵션이 모두 품절이면 품절이다")
    void stoppedOptionIgnored() {
        assertThat(DisplayStatus.of("ON_SALE", List.of(STOPPED, ON_SALE_SOLD_OUT))).isEqualTo(DisplayStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("판매 중 상품이라도 판매 중 옵션이 하나도 없으면(모두 중지 · 옵션 상태 없음) 판매 종료다")
    void noOnSaleOptionIsSaleEnded() {
        var unknownOption = new ReplicaProduct.OptionState(null, false);

        assertThat(DisplayStatus.of("ON_SALE", List.of(STOPPED, STOPPED))).isEqualTo(DisplayStatus.SALE_ENDED);
        assertThat(DisplayStatus.of("ON_SALE", List.of(unknownOption))).isEqualTo(DisplayStatus.SALE_ENDED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("판매 중 상품이라도 옵션이 없으면(빈 목록 · null) 판매 종료다")
    void noOptionsIsSaleEnded(List<ReplicaProduct.OptionState> options) {
        assertThat(DisplayStatus.of("ON_SALE", options)).isEqualTo(DisplayStatus.SALE_ENDED);
    }

    @Test
    @DisplayName("만들 때 이벤트 값과 표시 상태를 저장한다")
    void create() {
        ReplicaProduct replica = ReplicaProduct.create(values("상품 A", "ON_SALE", List.of(ON_SALE), OCCURRED_AT));

        assertThat(replica.getProductId()).isEqualTo(1L);
        assertThat(replica.getName()).isEqualTo("상품 A");
        assertThat(replica.getThumbnailUrl()).isEqualTo("https://img/1.png");
        assertThat(replica.getDisplayStatus()).isEqualTo(DisplayStatus.ON_SALE);
        assertThat(replica.getSourceOccurredAt()).isEqualTo(STORED);
    }

    @Test
    @DisplayName("저장된 시각보다 늦은 이벤트(1마이크로초 뒤)는 덮어쓴다")
    void newerApplied() {
        ReplicaProduct replica = ReplicaProduct.create(values("상품 A", "ON_SALE", List.of(ON_SALE), OCCURRED_AT));
        LocalDateTime later = STORED.plusNanos(1_000);

        boolean applied = replica.applyIfNewer(values("상품 B", "STOPPED", List.of(ON_SALE), later));

        assertThat(applied).isTrue();
        assertThat(replica.getName()).isEqualTo("상품 B");
        assertThat(replica.getDisplayStatus()).isEqualTo(DisplayStatus.SALE_ENDED);
        assertThat(replica.getSourceOccurredAt()).isEqualTo(later);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1_000})
    @DisplayName("저장된 시각과 같거나(중복) 1마이크로초 이른(늦게 온 옛) 이벤트는 무시한다")
    void sameOrOlderIgnored(long nanos) {
        ReplicaProduct replica = ReplicaProduct.create(values("상품 A", "ON_SALE", List.of(ON_SALE), OCCURRED_AT));

        boolean applied = replica.applyIfNewer(values("상품 B", "STOPPED", List.of(ON_SALE),
            OCCURRED_AT.plusNanos(nanos)));

        assertThat(applied).isFalse();
        assertThat(replica.getName()).isEqualTo("상품 A");
        assertThat(replica.getDisplayStatus()).isEqualTo(DisplayStatus.ON_SALE);
        assertThat(replica.getSourceOccurredAt()).isEqualTo(STORED);
    }

    @Test
    @DisplayName("발생 시각은 마이크로초로 잘라 저장한다(DB 정밀도와 같게)")
    void occurredAtTruncatedToMicros() {
        ReplicaProduct replica = ReplicaProduct.create(values("상품 A", "ON_SALE", List.of(ON_SALE), OCCURRED_AT));

        assertThat(replica.getSourceOccurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_000));
    }

    @Test
    @DisplayName("마이크로초 아래만 다른 두 시각은 같은 시각이라 덮어쓰지 않고, 1마이크로초 늦으면 반영한다")
    void subMicrosecondIsSameTime() {
        ReplicaProduct replica = ReplicaProduct.create(values("상품 A", "ON_SALE", List.of(ON_SALE),
            LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_000)));

        boolean sameMicro = replica.applyIfNewer(values("상품 B", "STOPPED", List.of(ON_SALE),
            LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_999)));
        boolean oneMicroLater = replica.applyIfNewer(values("상품 C", "STOPPED", List.of(ON_SALE),
            LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_457_000)));

        assertThat(sameMicro).isFalse();
        assertThat(oneMicroLater).isTrue();
        assertThat(replica.getName()).isEqualTo("상품 C");
    }

    private static ReplicaProduct.Values values(String name, String status, List<ReplicaProduct.OptionState> options,
        LocalDateTime occurredAt) {
        return new ReplicaProduct.Values(1L, name, "https://img/1.png", status, options, occurredAt);
    }
}
