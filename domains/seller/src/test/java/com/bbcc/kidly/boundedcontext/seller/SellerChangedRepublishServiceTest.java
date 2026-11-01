package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.seller.app.SellerChangedRepublishService;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerChangedRepublishServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SellerChangedRepublishService sellerChangedRepublishService;

    @Test
    @DisplayName("활성 · 정지 판매자마다 지금 상태로 SellerChangedEvent를 한 번씩 발행하고, 보낸 판매자 수를 돌려준다")
    void republishAllSellers() {
        var active = SellerFixture.activeSeller(1L);
        var suspended = SellerFixture.activeSeller(2L);
        suspended.suspend(SellerFixture.ADMIN_ID, "허위 상품 등록 신고", SellerFixture.NOW);
        given(sellerRepository.findAll()).willReturn(List.of(active, suspended));

        int publishedCount = sellerChangedRepublishService.republishAll();

        assertThat(publishedCount).isEqualTo(2);
        var captor = ArgumentCaptor.forClass(SellerChangedEvent.class);
        then(eventPublisher).should(times(2)).publish(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(event -> event.data().sellerId(), event -> event.data().businessName(),
                event -> event.data().status())
            .containsExactly(
                tuple(1L, SellerFixture.BUSINESS_NAME, SellerStatus.ACTIVE),
                tuple(2L, SellerFixture.BUSINESS_NAME, SellerStatus.SUSPENDED));
    }

    @Test
    @DisplayName("다시 보낸 이벤트는 판매자마다 eventId가 다르고, occurredAt은 지금이라 정지 시각보다 늦다")
    void republishedEventsAreNew() {
        var suspended = SellerFixture.activeSeller(1L);
        suspended.suspend(SellerFixture.ADMIN_ID, "허위 상품 등록 신고", SellerFixture.NOW);
        given(sellerRepository.findAll()).willReturn(List.of(suspended, SellerFixture.activeSeller(2L)));
        var before = LocalDateTime.now();

        sellerChangedRepublishService.republishAll();

        // 받는 쪽 복사본은 저장된 occurredAt보다 늦은 이벤트만 반영한다
        var captor = ArgumentCaptor.forClass(SellerChangedEvent.class);
        then(eventPublisher).should(times(2)).publish(captor.capture());
        var events = captor.getAllValues();
        assertThat(events.get(0).eventId()).isNotEqualTo(events.get(1).eventId());
        assertThat(events).allSatisfy(event -> assertThat(event.occurredAt()).isAfterOrEqualTo(before));
    }

    @Test
    @DisplayName("판매자가 없으면 아무것도 발행하지 않고 0을 돌려준다")
    void republishNoSellers() {
        given(sellerRepository.findAll()).willReturn(List.of());

        assertThat(sellerChangedRepublishService.republishAll()).isZero();
        then(eventPublisher).should(never()).publish(any());
    }
}
