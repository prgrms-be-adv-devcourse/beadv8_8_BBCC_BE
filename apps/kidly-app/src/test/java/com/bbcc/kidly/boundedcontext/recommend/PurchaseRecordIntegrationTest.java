package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.app.purchase.PurchaseRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.purchase.JpaPurchaseRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 구매 기록 서비스가 실제 DB에서 구매확정을 저장하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendPurchaseKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class PurchaseRecordIntegrationTest {

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaPurchaseRecordRepository purchaseRecordRepository;

    @Autowired
    private PurchaseRecordService purchaseRecordService;

    @Test
    @DisplayName("INV-R03 자녀를 고르지 않은 구매확정은 예외 없이 구매 기록으로 저장하지 않는다")
    void skipConfirmationWithoutChild() {
        assertThatCode(() -> purchaseRecordService.recordPurchaseConfirmed(
            RecommendFixture.purchaseConfirmedEventFor(null)))
            .doesNotThrowAnyException();

        assertThat(purchaseRecordRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("INV-R03 같은 구매확정을 다시 받아도 예외 없이 구매 기록은 한 줄이다")
    void keepOneRecordForSameConfirmation() {
        purchaseRecordService.recordPurchaseConfirmed(RecommendFixture.purchaseConfirmedEvent());

        assertThatCode(() -> purchaseRecordService.recordPurchaseConfirmed(RecommendFixture.purchaseConfirmedEvent()))
            .doesNotThrowAnyException();

        assertThat(purchaseRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("INV-R03 같은 주문상품의 구매 기록을 두 줄 저장하면 UNIQUE 제약이 막는다")
    void rejectSecondRecordOfSameOrderItem() {
        purchaseRecordRepository.saveAndFlush(PurchaseRecord.create(RecommendFixture.purchaseConfirmation()));

        assertThatThrownBy(
            () -> purchaseRecordRepository.saveAndFlush(PurchaseRecord.create(RecommendFixture.purchaseConfirmation())))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(purchaseRecordRepository.findAll()).hasSize(1);
    }
}
