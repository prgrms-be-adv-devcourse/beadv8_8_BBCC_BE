package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductReplicaSellerFixture.SELLER_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.product.ProductReplicaSellerFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판매자 정보 변경됨을 받아 상품의 판매자 복사본에 반영하는 흐름을 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class ProductReplicaSellerIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductReplicaSellerRepository replicaSellerRepository;

    // 리스너의 예외는 커밋 뒤라 로그로만 남으므로, 같은 이벤트를 두 번 받을 때는 서비스를 직접 불러 예외까지 확인한다
    @Autowired
    private ProductReplicaSellerService replicaSellerService;

    @Test
    @DisplayName("판매자 정보 변경됨을 발행한 트랜잭션이 커밋되면 리스너가 자기 트랜잭션에서 판매자 복사본을 저장한다")
    void saveReplicaAfterCommit() {
        publishInTransaction(ProductReplicaSellerFixture.sellerApprovedEvent());

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                ProductReplicaSeller::getSellerId,
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly(SELLER_ID, "테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @Test
    @DisplayName("판매자 정보 변경됨을 발행한 트랜잭션이 롤백되면 판매자 복사본이 생기지 않는다")
    void noReplicaAfterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(ProductReplicaSellerFixture.sellerApprovedEvent());
            status.setRollbackOnly();
        });

        assertThat(replicaSellerRepository.findById(SELLER_ID)).isEmpty();
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
        publishInTransaction(ProductReplicaSellerFixture.sellerApprovedEvent());

        publishInTransaction(
            ProductReplicaSellerFixture.sellerChangedEventAt("새상점", SellerStatus.SUSPENDED, suspendedAt));
        publishInTransaction(
            ProductReplicaSellerFixture.sellerChangedEventAt("옛상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT));

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("새상점", SellerStatus.SUSPENDED, suspendedAt);
    }

    @Test
    @DisplayName("판매자 복사본 반영이 실패해도 발행한 트랜잭션은 성공하고 복사본은 이전 값 그대로다")
    void keepReplicaWhenListenerFails() {
        publishInTransaction(ProductReplicaSellerFixture.sellerApprovedEvent());
        SellerChangedEvent withoutBusinessName = ProductReplicaSellerFixture.sellerChangedEventAt(
            null, SellerStatus.SUSPENDED, SELLER_CHANGED_AT.plusMinutes(1));

        assertThatCode(() -> publishInTransaction(withoutBusinessName)).doesNotThrowAnyException();

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                ProductReplicaSeller::getBusinessName,
                ProductReplicaSeller::getStatus,
                ProductReplicaSeller::getEventOccurredAt)
            .containsExactly("테스트상점", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    private void publishInTransaction(SellerChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
