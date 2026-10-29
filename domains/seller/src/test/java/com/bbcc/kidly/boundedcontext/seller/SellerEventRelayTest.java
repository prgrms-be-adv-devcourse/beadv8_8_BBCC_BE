package com.bbcc.kidly.boundedcontext.seller;

import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.BUSINESS_NAME;
import static com.bbcc.kidly.boundedcontext.seller.SellerFixture.NOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.seller.app.SellerEventRelay;
import com.bbcc.kidly.boundedcontext.seller.app.port.SellerEventSender;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerEventRelayTest {

    @Mock
    private SellerEventSender sellerEventSender;

    @InjectMocks
    private SellerEventRelay sellerEventRelay;

    @Test
    @DisplayName("판매자가 발행한 이벤트는 Kafka로 넘긴다")
    void relaySellerEvent() {
        var event = new SellerChangedEvent(UUID.randomUUID(), NOW,
            new SellerChangedEvent.Data(1L, BUSINESS_NAME, SellerStatus.ACTIVE));

        sellerEventRelay.relay(event);

        then(sellerEventSender).should().send(event);
    }

    @Test
    @DisplayName("다른 도메인이 발행한 이벤트는 넘기지 않는다")
    void skipOtherProducer() {
        var event = new MemberChangedEvent(UUID.randomUUID(), NOW, new MemberChangedEvent.Data(1L, "새닉네임"));

        sellerEventRelay.relay(event);

        then(sellerEventSender).should(never()).send(any());
    }
}
