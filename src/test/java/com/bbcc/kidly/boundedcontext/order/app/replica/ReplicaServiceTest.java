package com.bbcc.kidly.boundedcontext.order.app.replica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplicaServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Mock
    private ProductReplicaRepository productReplicaRepository;

    @Mock
    private ChildReplicaRepository childReplicaRepository;

    @InjectMocks
    private ReplicaService replicaService;

    @Captor
    private ArgumentCaptor<ReplicaProduct> savedProduct;

    @Captor
    private ArgumentCaptor<ReplicaChild> savedChild;

    // ── 상품 ──

    @Test
    @DisplayName("처음 받은 상품은 옵션마다 복사본 1행을 만든다")
    void createsOneRowPerOption() {
        replicaService.applyProductChanged(product(OCCURRED_AT, "ON_SALE",
            option(1L, "110", 25_000L, false), option(2L, "120", 25_000L, true)));

        verify(productReplicaRepository, times(2)).save(savedProduct.capture());
        List<ReplicaProduct> rows = savedProduct.getAllValues();
        ReplicaProduct first = rows.get(0);
        assertThat(first.getOptionId()).isEqualTo(1L);
        assertThat(first.getProductId()).isEqualTo(1L);
        assertThat(first.getSellerId()).isEqualTo(2L);
        assertThat(first.getProductName()).isEqualTo("상품 A");
        assertThat(first.getCategoryId()).isEqualTo(1L);
        assertThat(first.getSize()).isEqualTo("110");
        assertThat(first.unitPrice()).isEqualTo(25_000L);
        assertThat(first.getProductStatus()).isEqualTo("ON_SALE");
        assertThat(first.isSoldOut()).isFalse();
        assertThat(first.getEventOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(rows.get(1).getOptionId()).isEqualTo(2L);
        assertThat(rows.get(1).isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("판매 상태 이름을 그대로 저장한다")
    void storesProductStatus() {
        replicaService.applyProductChanged(product(OCCURRED_AT, "STOPPED", option(1L, "110", 25_000L, false)));

        verify(productReplicaRepository).save(savedProduct.capture());
        assertThat(savedProduct.getValue().getProductStatus()).isEqualTo("STOPPED");
        assertThat(savedProduct.getValue().isPurchasable()).isFalse();
    }

    @Test
    @DisplayName("이미 있는 옵션은 늦게 발생한 변경이면 그 행을 덮어쓴다")
    void updatesExistingProductRow() {
        ReplicaProduct existing = ReplicaProduct.create(productValues(1L, 25_000L, OCCURRED_AT));
        when(productReplicaRepository.findAllByProductId(1L)).thenReturn(List.of(existing));

        replicaService.applyProductChanged(product(OCCURRED_AT.plusMinutes(1), "ON_SALE",
            option(1L, "110", 23_000L, false)));

        assertThat(existing.unitPrice()).isEqualTo(23_000L);
        verify(productReplicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("같거나 이른 시각의 상품 변경은 무시한다")
    void ignoresStaleProductChange() {
        ReplicaProduct existing = ReplicaProduct.create(productValues(1L, 25_000L, OCCURRED_AT));
        when(productReplicaRepository.findAllByProductId(1L)).thenReturn(List.of(existing));

        replicaService.applyProductChanged(product(OCCURRED_AT, "ON_SALE", option(1L, "110", 23_000L, false)));

        assertThat(existing.unitPrice()).isEqualTo(25_000L);
        verify(productReplicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 상품의 변경에서 빠진 옵션은 행을 남기고 품절로 바꾼다")
    void marksMissingOptionSoldOut() {
        ReplicaProduct kept = ReplicaProduct.create(productValues(1L, 25_000L, OCCURRED_AT));
        ReplicaProduct missing = ReplicaProduct.create(productValues(2L, 25_000L, OCCURRED_AT));
        when(productReplicaRepository.findAllByProductId(1L)).thenReturn(List.of(kept, missing));

        replicaService.applyProductChanged(product(OCCURRED_AT.plusMinutes(1), "ON_SALE",
            option(1L, "110", 25_000L, false)));

        assertThat(kept.isSoldOut()).isFalse();
        assertThat(missing.isSoldOut()).isTrue();
        assertThat(missing.getEventOccurredAt()).isEqualTo(OCCURRED_AT.plusMinutes(1));
    }

    // ── 자녀 ──

    @Test
    @DisplayName("처음 받은 자녀는 복사본을 만든다")
    void createsChildRow() {
        replicaService.applyChildChanged(child("115.0", "NORMAL", false, OCCURRED_AT));

        verify(childReplicaRepository).save(savedChild.capture());
        ReplicaChild row = savedChild.getValue();
        assertThat(row.getChildId()).isEqualTo(1L);
        assertThat(row.getMemberId()).isEqualTo(1L);
        assertThat(row.getName()).isNull();
        assertThat(row.getHeightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
        assertThat(row.getWeightKg()).isEqualByComparingTo(new BigDecimal("20.0"));
        assertThat(row.getBodyType()).isEqualTo("NORMAL");
        assertThat(row.isDeleted()).isFalse();
        assertThat(row.getEventOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("체형이 없으면 null로 저장한다")
    void storesNullBodyType() {
        replicaService.applyChildChanged(child("115.0", null, false, OCCURRED_AT));

        verify(childReplicaRepository).save(savedChild.capture());
        assertThat(savedChild.getValue().getBodyType()).isNull();
    }

    @Test
    @DisplayName("이미 있는 자녀는 늦게 발생한 변경이면 그 행을 덮어쓴다. 삭제도 행을 남기고 표시만 한다")
    void updatesExistingChildRow() {
        ReplicaChild existing = ReplicaChild.create(childValues("115.0", OCCURRED_AT));
        when(childReplicaRepository.findByChildId(1L)).thenReturn(Optional.of(existing));

        replicaService.applyChildChanged(child("118.5", "NORMAL", true, OCCURRED_AT.plusDays(1)));

        assertThat(existing.getHeightCm()).isEqualByComparingTo(new BigDecimal("118.5"));
        assertThat(existing.isDeleted()).isTrue();
        verify(childReplicaRepository, never()).save(any());
    }

    @Test
    @DisplayName("같거나 이른 시각의 자녀 변경은 무시한다")
    void ignoresStaleChildChange() {
        ReplicaChild existing = ReplicaChild.create(childValues("115.0", OCCURRED_AT));
        when(childReplicaRepository.findByChildId(1L)).thenReturn(Optional.of(existing));

        replicaService.applyChildChanged(child("118.5", "NORMAL", true, OCCURRED_AT));

        assertThat(existing.getHeightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
        assertThat(existing.isDeleted()).isFalse();
    }

    private static ProductChangedCommand product(LocalDateTime occurredAt, String status,
        ProductChangedCommand.Option... options) {
        return new ProductChangedCommand(1L, 2L, "상품 A", 1L, status, List.of(options), occurredAt);
    }

    private static ProductChangedCommand.Option option(Long optionId, String size, long unitPrice, boolean soldOut) {
        return new ProductChangedCommand.Option(optionId, size, unitPrice, soldOut);
    }

    private static ReplicaProduct.Values productValues(Long optionId, long unitPrice, LocalDateTime occurredAt) {
        return new ReplicaProduct.Values(optionId, 1L, 2L, "상품 A", "110", unitPrice, 1L, "ON_SALE", false,
            occurredAt);
    }

    private static ChildChangedCommand child(String height, String bodyType, boolean deleted,
        LocalDateTime occurredAt) {
        return new ChildChangedCommand(1L, 1L, null, new BigDecimal(height), new BigDecimal("20.0"), bodyType,
            deleted, occurredAt);
    }

    private static ReplicaChild.Values childValues(String height, LocalDateTime occurredAt) {
        return new ReplicaChild.Values(1L, 1L, null, new BigDecimal(height), new BigDecimal("20.0"), "NORMAL",
            false, occurredAt);
    }
}
