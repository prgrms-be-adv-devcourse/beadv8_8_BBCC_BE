package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.SELLER_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaSellerService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판매자 정보 변경됨을 받아 판매자 복사본에 반영하는 흐름을 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class RecommendReplicaSellerIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RecommendReplicaSellerRepository replicaSellerRepository;

    // 리스너의 예외는 커밋 뒤라 로그로만 남으므로, 반영 규칙은 서비스를 직접 불러 예외까지 확인한다
    @Autowired
    private RecommendReplicaSellerService replicaSellerService;

    @Test
    @DisplayName("판매자 정보 변경됨을 발행한 트랜잭션이 커밋되면 이벤트 값이 판매자 복사본에 저장된다")
    void saveReplicaAfterCommit() {
        publishInTransaction(RecommendReplicaSellerFixture.sellerApprovedEvent());

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @Test
    @DisplayName("판매자 정보 변경됨을 발행한 트랜잭션이 롤백되면 판매자 복사본이 생기지 않는다")
    void noReplicaAfterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(RecommendReplicaSellerFixture.sellerApprovedEvent());
            status.setRollbackOnly();
        });

        assertThat(replicaSellerRepository.findById(SELLER_ID)).isEmpty();
    }

    @Test
    @DisplayName("판매자 복사본 반영이 실패해도 발행한 트랜잭션은 성공하고 복사본은 이전 값 그대로다")
    void keepReplicaWhenListenerFails() {
        publishInTransaction(RecommendReplicaSellerFixture.sellerApprovedEvent());
        SellerChangedEvent withoutBusinessName = RecommendReplicaSellerFixture.sellerChangedEventAt(
            null, SellerStatus.SUSPENDED, SELLER_CHANGED_AT.plusMinutes(1));

        assertThatCode(() -> publishInTransaction(withoutBusinessName)).doesNotThrowAnyException();

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    @Test
    @DisplayName("INV-R01 이미 있는 판매자 복사본은 더 늦게 일어난 이벤트의 값으로 덮어써진다")
    void overwriteWithLaterEvent() {
        LocalDateTime later = SELLER_CHANGED_AT.plusMinutes(1);
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());

        replicaSellerService.applySellerChanged(
            RecommendReplicaSellerFixture.sellerChangedEventAt("새옷장", SellerStatus.SUSPENDED, later));

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("새옷장", SellerStatus.SUSPENDED, later);
    }

    // 같은 이벤트를 두 번 받는 경우도 발생 시각이 같으므로 0분 경우가 확인한다. 값을 바꿔 보내야 무시됐는지 알 수 있다
    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-R01 같은 시각이거나 더 이르게 일어난 이벤트를 받아도 예외 없이 판매자 복사본이 그대로다")
    void keepReplicaForEventNotLater(long minutesFromStored) {
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());

        assertThatCode(() -> replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerChangedEventAt(
            "새옷장", SellerStatus.SUSPENDED, SELLER_CHANGED_AT.plusMinutes(minutesFromStored))))
            .doesNotThrowAnyException();

        assertThat(replicaSellerRepository.findById(SELLER_ID)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    private void publishInTransaction(SellerChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
