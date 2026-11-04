package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductReplicaSellerFixture.SELLER_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductReplicaSellerFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 판매자 정보 변경됨을 받아 상품의 판매자 복사본에 반영하는 흐름을 확인한다.
 * 수신은 Kafka로만 하므로(ProductKafkaReceiveIntegrationTest) 여기서는 처리 서비스를 직접 부른다.
 */
@IntegrationTest
class ProductReplicaSellerIntegrationTest {

    @Autowired
    private ProductReplicaSellerService replicaSellerService;

    @Autowired
    private ProductReplicaSellerRepository replicaSellerRepository;

    @Test
    @DisplayName("판매자 정보 변경을 처리하면 판매자 복사본을 저장한다")
    void saveReplica() {
        replicaSellerService.applySellerChanged(ProductReplicaSellerFixture.sellerApprovedEvent());

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                ProductReplicaSeller::getSellerId,
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly(SELLER_ID, "테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @Test
    @DisplayName("같은 판매자 정보 변경됨을 두 번 받아도 예외 없이 판매자 복사본은 한 번 반영한 값 그대로다")
    void keepReplicaWhenSameEventArrivesTwice() {
        SellerChangedEvent event = ProductReplicaSellerFixture.sellerApprovedEvent();
        replicaSellerService.applySellerChanged(event);

        assertThatCode(() -> replicaSellerService.applySellerChanged(event)).doesNotThrowAnyException();

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @Test
    @DisplayName("더 늦게 일어난 판매자 정보 변경됨은 덮어쓰고, 그 뒤 늦게 도착한 이전 이벤트는 무시한다")
    void overwriteWithLaterEventAndIgnoreEarlierEvent() {
        LocalDateTime suspendedAt = SELLER_CHANGED_AT.plusDays(1);
        replicaSellerService.applySellerChanged(ProductReplicaSellerFixture.sellerApprovedEvent());

        replicaSellerService.applySellerChanged(
            ProductReplicaSellerFixture.sellerChangedEventAt("새상점", SellerStatus.SUSPENDED, suspendedAt));
        replicaSellerService.applySellerChanged(
            ProductReplicaSellerFixture.sellerChangedEventAt("옛상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT));

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("새상점", SellerStatus.SUSPENDED, suspendedAt);
    }

    @Test
    @DisplayName("판매자 복사본 반영이 실패하면 복사본은 이전 값 그대로다")
    void keepReplicaWhenApplyFails() {
        replicaSellerService.applySellerChanged(ProductReplicaSellerFixture.sellerApprovedEvent());
        SellerChangedEvent withoutBusinessName = ProductReplicaSellerFixture.sellerChangedEventAt(
            null, SellerStatus.SUSPENDED, SELLER_CHANGED_AT.plusMinutes(1));

        assertThatThrownBy(() -> replicaSellerService.applySellerChanged(withoutBusinessName))
            .isInstanceOf(RuntimeException.class);

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }
}
