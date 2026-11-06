package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.domain.ProductName;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameChange;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameInfo;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductNameTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 11, 5, 9, 0);

    @Test
    @DisplayName("더 늦게 발생한 이벤트면 이름을 덮어쓴다")
    void applyNewer() {
        var productName = ProductName.create(info("곰돌이 티셔츠", OCCURRED_AT));

        productName.applyIfNewer(info("곰돌이 반팔 티셔츠", OCCURRED_AT.plusSeconds(1)));

        assertThat(productName.getName()).isEqualTo("곰돌이 반팔 티셔츠");
        assertThat(productName.getSourceOccurredAt()).isEqualTo(OCCURRED_AT.plusSeconds(1));
    }

    @Test
    @DisplayName("더 일찍 발생한 옛 이벤트는 무시하고, 관리자 판매 중지 · 품절이어도 알릴 변화가 없다")
    void ignoreOlder() {
        var productName = ProductName.create(info("곰돌이 티셔츠", OCCURRED_AT));

        var change = productName.applyIfNewer(
            new ProductNameInfo(1L, "옛 이름", ProductStatus.STOPPED, true, Set.of(), Set.of(11L, 12L),
                OCCURRED_AT.minusSeconds(1)));

        assertThat(change).isEqualTo(ProductNameChange.NONE);
        assertThat(productName.getName()).isEqualTo("곰돌이 티셔츠");
        assertThat(productName.getSourceOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("발생 시각은 마이크로초로 잘라, 같은 메시지를 다시 받으면 늦은 이벤트로 보지 않는다")
    void sameMessageWithNanos() {
        var occurredAt = OCCURRED_AT.plusNanos(123_456_789);
        var productName = ProductName.create(info("곰돌이 티셔츠", occurredAt));

        productName.applyIfNewer(info("다른 이름", occurredAt));

        assertThat(productName.getName()).isEqualTo("곰돌이 티셔츠");
        assertThat(productName.getSourceOccurredAt()).isEqualTo(OCCURRED_AT.plusNanos(123_456_000));
    }

    @Test
    @DisplayName("관리자가 새로 판매를 중지하면 알리고, 이미 관리자 중지였으면 알리지 않는다")
    void stoppedByAdmin() {
        var productName = ProductName.create(info("곰돌이 티셔츠", OCCURRED_AT));

        var stopped = productName.applyIfNewer(stoppedInfo(OCCURRED_AT.plusSeconds(1)));
        var again = productName.applyIfNewer(stoppedInfo(OCCURRED_AT.plusSeconds(2)));

        assertThat(stopped.newlyStoppedByAdmin()).isTrue();
        assertThat(again.newlyStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("판매자 중지 뒤 관리자 중지 상태가 와도 알리지 않는다")
    void sellerStoppedThenAdmin() {
        var productName = ProductName.create(new ProductNameInfo(1L, "상품", ProductStatus.STOPPED, false,
            Set.of(11L), Set.of(), OCCURRED_AT));

        assertThat(productName.applyIfNewer(stoppedInfo(OCCURRED_AT.plusSeconds(1))).newlyStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("등록 취소에서 관리자 중지 상태로 돌아와도 알리지 않는다")
    void delistedThenStopped() {
        var productName = ProductName.create(new ProductNameInfo(1L, "상품", ProductStatus.DELISTED, true,
            Set.of(11L), Set.of(), OCCURRED_AT));

        assertThat(productName.applyIfNewer(stoppedInfo(OCCURRED_AT.plusSeconds(1))).newlyStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("재고가 있던 옵션이 품절되면 그 옵션만 알리고, 이미 품절이던 옵션 · 보관해 빠진 옵션은 알리지 않는다")
    void soldOut() {
        var productName = ProductName.create(
            new ProductNameInfo(1L, "곰돌이 티셔츠", ProductStatus.ON_SALE, false, Set.of(11L, 12L, 13L),
                Set.of(14L), OCCURRED_AT));

        var change = productName.applyIfNewer(
            new ProductNameInfo(1L, "곰돌이 티셔츠", ProductStatus.ON_SALE, false, Set.of(12L),
                Set.of(11L, 14L), OCCURRED_AT.plusSeconds(1)));

        assertThat(change.newlySoldOutOptionIds()).containsExactly(11L);
        assertThat(change.newlyStoppedByAdmin()).isFalse();
    }

    @Test
    @DisplayName("판매 상태 칸을 더하기 전에 저장한 행은 다음 이벤트로 기준만 잡고 알리지 않는다")
    void unknownStateOnlyTakesBaseline() {
        var productName = ProductName.create(info("곰돌이 티셔츠", OCCURRED_AT));
        ReflectionTestUtils.setField(productName, "inStockOptionIds", null);

        var change = productName.applyIfNewer(
            new ProductNameInfo(1L, "곰돌이 티셔츠", ProductStatus.STOPPED, true, Set.of(), Set.of(11L),
                OCCURRED_AT.plusSeconds(1)));

        assertThat(change).isEqualTo(ProductNameChange.NONE);
        assertThat(productName.getInStockOptionIds()).isEmpty();
        assertThat(productName.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    private static ProductNameInfo info(String name, LocalDateTime occurredAt) {
        return new ProductNameInfo(1L, name, ProductStatus.ON_SALE, false, Set.of(11L), Set.of(), occurredAt);
    }

    private static ProductNameInfo stoppedInfo(LocalDateTime occurredAt) {
        return new ProductNameInfo(1L, "곰돌이 티셔츠", ProductStatus.STOPPED, true, Set.of(11L), Set.of(), occurredAt);
    }
}
