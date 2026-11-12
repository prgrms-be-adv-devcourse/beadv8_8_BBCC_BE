package com.bbcc.kidly.boundedcontext.order.app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Limit;

/**
 * 판매 불가 판매자 정리(#1121) 판단 · 커서: ACTIVE인지 하나만 보고(그 밖의 상태는 판매 불가), 복사본이 없으면 하지 않는다.
 * 한 주문이 실패해도 나머지를 처리하고 끝에 예외로 알린다.
 */
class SellerUnavailableCleanupServiceTest {

    private static final Long SELLER_ID = 7L;

    private final SellerReplicaRepository sellers = mock(SellerReplicaRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderService orderService = mock(OrderService.class);
    private final SellerUnavailableCleanupService service =
        new SellerUnavailableCleanupService(sellers, orders, orderService);

    @Test
    @DisplayName("복사본이 ACTIVE거나 없으면 대상을 읽지도 않는다")
    void skipsActiveOrMissing() {
        givenSeller("ACTIVE");
        assertThat(service.cleanUpIfUnavailable(SELLER_ID)).isZero();
        when(sellers.findBySellerId(SELLER_ID)).thenReturn(Optional.empty());
        assertThat(service.cleanUpIfUnavailable(SELLER_ID)).isZero();
        assertThat(service.cleanUpIfUnavailable(null)).isZero();

        verifyNoInteractions(orders, orderService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SUSPENDED", "CLOSED"})
    @DisplayName("ACTIVE가 아닌 판매자 상태는 모두 판매 불가로 보고 대상 주문마다 정리를 부른다")
    void cleansUpForAnyOtherStatus(String status) {
        givenSeller(status);
        when(orders.findOrderIdsWithSellerItems(eq(SELLER_ID), eq(OrderItem.Status.ORDERED), any(), eq(0L), any()))
            .thenReturn(List.of(3L, 5L));

        assertThat(service.cleanUpIfUnavailable(SELLER_ID)).isEqualTo(2);

        verify(orderService).cancelForUnavailableSeller(3L, SELLER_ID);
        verify(orderService).cancelForUnavailableSeller(5L, SELLER_ID);
    }

    @Test
    @DisplayName("한 번에 100건씩 마지막 ID 다음부터 이어 읽고, 덜 찬 묶음에서 멈춘다")
    void pagesByCursor() {
        givenSeller("CLOSED");
        int size = SellerUnavailableCleanupService.BATCH_SIZE;
        List<Long> first = LongStream.rangeClosed(1, size).boxed().toList();
        when(orders.findOrderIdsWithSellerItems(eq(SELLER_ID), any(), any(), eq(0L), eq(Limit.of(size))))
            .thenReturn(first);
        when(orders.findOrderIdsWithSellerItems(eq(SELLER_ID), any(), any(), eq((long) size), eq(Limit.of(size))))
            .thenReturn(List.of(500L));

        assertThat(service.cleanUpIfUnavailable(SELLER_ID)).isEqualTo(size + 1);
    }

    @Test
    @DisplayName("한 주문이 실패해도 나머지를 처리하고, 끝에 예외를 던져 수신 쪽이 다시 처리하게 한다")
    void continuesThenThrows() {
        givenSeller("SUSPENDED");
        when(orders.findOrderIdsWithSellerItems(eq(SELLER_ID), any(), any(), anyLong(), any()))
            .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("잠금 충돌")).when(orderService).cancelForUnavailableSeller(2L, SELLER_ID);

        assertThatThrownBy(() -> service.cleanUpIfUnavailable(SELLER_ID)).isInstanceOf(IllegalStateException.class);

        verify(orderService).cancelForUnavailableSeller(1L, SELLER_ID);
        verify(orderService).cancelForUnavailableSeller(3L, SELLER_ID);
    }

    private void givenSeller(String status) {
        when(sellers.findBySellerId(SELLER_ID)).thenReturn(Optional.of(ReplicaSeller.create(
            new ReplicaSeller.Values(SELLER_ID, "키들리샵", status, LocalDateTime.of(2026, 11, 10, 9, 0)))));
    }
}
