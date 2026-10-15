package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CONFIRMED_AT;
import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementItemService;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItem;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementItemServiceTest {

    private static final OrderItemPurchaseConfirmedEvent.Data DATA = new OrderItemPurchaseConfirmedEvent.Data(
        101L, 1L, 2L, SELLER_ID, 3L, 4L, 5L, "120", 2, 50_000L, CONFIRMED_AT);

    @Mock
    private SettlementItemRepository settlementItemRepository;

    @InjectMocks
    private SettlementItemService settlementItemService;

    @Test
    @DisplayName("처음 받은 주문상품은 이벤트 값으로 대기 항목을 저장한다")
    void register() {
        given(settlementItemRepository.existsByOrderItemId(101L)).willReturn(false);

        settlementItemService.register(DATA);

        var captor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementItemRepository).save(captor.capture());
        assertThat(captor.getValue())
            .extracting(SettlementItem::getOrderId, SettlementItem::getOrderItemId, SettlementItem::getSellerId,
                SettlementItem::getSalesAmount, SettlementItem::getConfirmedAt, SettlementItem::getStatus)
            .containsExactly(1L, 101L, SELLER_ID, 50_000L, CONFIRMED_AT, SettlementItemStatus.PENDING);
    }

    @Test
    @DisplayName("이미 쌓인 주문상품이면 저장하지 않는다")
    void skipDuplicate() {
        given(settlementItemRepository.existsByOrderItemId(101L)).willReturn(true);

        settlementItemService.register(DATA);

        verify(settlementItemRepository, never()).save(any());
    }
}
