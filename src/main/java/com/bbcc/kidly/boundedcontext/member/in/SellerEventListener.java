package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.SellerStateService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 판매자가 승인 · 정지되면 판매자 상태 복사본을 갱신한다.
 */
@Component
@RequiredArgsConstructor
public class SellerEventListener {

    private final SellerStateService sellerStateService;

    @DomainEventListener
    public void on(SellerChangedEvent event) {
        var data = event.data();
        sellerStateService.apply(data.sellerId(), data.status(), event.occurredAt());
    }
}
