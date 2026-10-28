package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final SellerSuspendRequest REQUEST = new SellerSuspendRequest("허위 상품 등록 신고");

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private SellerService sellerService;

    @Test
    @DisplayName("정지하면 SellerChangedEvent를 한 번 발행한다. 발생 시각은 정지 시각과 같다")
    void suspend() {
        var seller = SellerFixture.activeSeller(SELLER_ID);
        given(sellerRepository.getById(SELLER_ID)).willReturn(seller);

        var result = sellerService.suspend(SELLER_ID, SellerFixture.ADMIN_ID, REQUEST);

        assertThat(result.status()).isEqualTo(SellerStatus.SUSPENDED);
        var captor = ArgumentCaptor.forClass(SellerChangedEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        var event = captor.getValue();
        assertThat(event.data().sellerId()).isEqualTo(SELLER_ID);
        assertThat(event.data().businessName()).isEqualTo(SellerFixture.BUSINESS_NAME);
        assertThat(event.data().status()).isEqualTo(SellerStatus.SUSPENDED);
        assertThat(event.occurredAt()).isEqualTo(seller.getSuspension().suspendedAt());
    }

    @Test
    @DisplayName("없는 판매자는 NOT_FOUND이고 이벤트를 발행하지 않는다")
    void suspendMissing() {
        given(sellerRepository.getById(SELLER_ID)).willThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> sellerService.suspend(SELLER_ID, SellerFixture.ADMIN_ID, REQUEST))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(CommonErrorCode.NOT_FOUND);
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("이미 정지된 판매자는 SELLER_ALREADY_SUSPENDED이고 이벤트를 발행하지 않는다")
    void suspendTwice() {
        var seller = SellerFixture.activeSeller(SELLER_ID);
        seller.suspend(SellerFixture.ADMIN_ID, "먼저 정지", LocalDateTime.now());
        given(sellerRepository.getById(SELLER_ID)).willReturn(seller);

        assertThatThrownBy(() -> sellerService.suspend(SELLER_ID, SellerFixture.ADMIN_ID, REQUEST))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(SellerErrorCode.SELLER_ALREADY_SUSPENDED);
        then(eventPublisher).should(never()).publish(any());
    }
}
