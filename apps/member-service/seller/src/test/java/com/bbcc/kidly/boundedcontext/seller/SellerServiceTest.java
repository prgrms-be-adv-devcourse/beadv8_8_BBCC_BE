package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.boundedcontext.seller.domain.Seller;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerErrorCode;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final SellerSuspendRequest REQUEST = new SellerSuspendRequest("허위 상품 등록 신고");

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        then(eventPublisher).should().publishEvent(captor.capture());
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
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
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
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }
    @Test
    @DisplayName("활성 판매자를 닫으면 CLOSED 이벤트를 발행한다")
    void closeActive() {
        var seller = SellerFixture.activeSeller(SELLER_ID);
        given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

        sellerService.closeIfOpen(SELLER_ID);

        assertClosedAndPublished(seller);
    }

    @Test
    @DisplayName("정지된 판매자를 닫으면 CLOSED 이벤트를 발행한다")
    void closeSuspended() {
        var seller = SellerFixture.activeSeller(SELLER_ID);
        seller.suspend(SellerFixture.ADMIN_ID, "정지", LocalDateTime.now());
        given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

        sellerService.closeIfOpen(SELLER_ID);

        assertClosedAndPublished(seller);
    }

    @Test
    @DisplayName("이미 닫힌 판매자는 이벤트를 발행하지 않는다")
    void closeAlreadyClosed() {
        var seller = SellerFixture.activeSeller(SELLER_ID);
        seller.close();
        given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

        sellerService.closeIfOpen(SELLER_ID);

        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("판매자가 없으면 이벤트를 발행하지 않는다")
    void closeMissing() {
        given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.empty());

        sellerService.closeIfOpen(SELLER_ID);

        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    private void assertClosedAndPublished(Seller seller) {
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.CLOSED);
        var captor = ArgumentCaptor.forClass(SellerChangedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().data().status()).isEqualTo(SellerStatus.CLOSED);
    }
}
