package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.SELLER_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaSellerService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSellerRepository;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 판매자 복사본 서비스가 실제 DB에서 판매자 정보 변경됨을 반영하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendKafkaListenerIntegrationTest가 확인한다.
 */
@IntegrationTest
class RecommendReplicaSellerIntegrationTest {

    @Autowired
    private RecommendReplicaSellerRepository replicaSellerRepository;

    @Autowired
    private RecommendReplicaSellerService replicaSellerService;

    @Test
    @DisplayName("INV-R01 이미 있는 판매자 복사본은 더 늦게 일어난 이벤트의 값으로 덮어써진다")
    void overwriteWithLaterEvent() {
        LocalDateTime later = SELLER_CHANGED_AT.plusMinutes(1);
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());

        replicaSellerService.applySellerChanged(
            RecommendReplicaSellerFixture.sellerChangedEventAt("새옷장", SellerStatus.SUSPENDED, later));

        assertThat(replicaSellerRepository.findById(PRODUCT_A_SELLER_ID)).get()
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

        assertThat(replicaSellerRepository.findById(PRODUCT_A_SELLER_ID)).get()
            .extracting(
                RecommendReplicaSeller::getBusinessName,
                RecommendReplicaSeller::getStatus,
                RecommendReplicaSeller::getEventOccurredAt)
            .containsExactly("소소한옷장", SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }
}
