package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 자녀 복사본 서비스가 실제 DB에서 자녀 정보 변경됨을 반영하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendKafkaListenerIntegrationTest가 확인한다.
 */
@IntegrationTest
class RecommendReplicaChildIntegrationTest {

    @Autowired
    private RecommendReplicaChildRepository replicaChildRepository;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Test
    @DisplayName("INV-R01 이미 있는 자녀 복사본은 더 늦게 일어난 이벤트의 값으로 덮어써진다")
    void overwriteWithLaterEvent() {
        replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventAt(new BigDecimal("115.0"), CHILD_CHANGED_AT));

        replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventAt(new BigDecimal("120.0"), CHILD_CHANGED_AT.plusMinutes(1)));

        RecommendReplicaChild replica = replicaChildRepository.findById(CHILD_1_ID).orElseThrow();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("120.0");
        assertThat(replica.getEventOccurredAt()).isEqualTo(CHILD_CHANGED_AT.plusMinutes(1));
    }

    @ParameterizedTest(name = "저장된 시각에서 {0}분")
    @ValueSource(longs = {0, -1})
    @DisplayName("INV-R01 같은 시각이거나 더 이르게 일어난 이벤트를 받아도 예외 없이 자녀 복사본이 그대로다")
    void keepReplicaForEventNotLater(long minutesFromStored) {
        replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventAt(new BigDecimal("115.0"), CHILD_CHANGED_AT));

        assertThatCode(() -> replicaChildService.applyChildChanged(RecommendFixture.childChangedEventAt(
            new BigDecimal("120.0"), CHILD_CHANGED_AT.plusMinutes(minutesFromStored))))
            .doesNotThrowAnyException();

        RecommendReplicaChild replica = replicaChildRepository.findById(CHILD_1_ID).orElseThrow();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("115.0");
        assertThat(replica.getEventOccurredAt()).isEqualTo(CHILD_CHANGED_AT);
    }

    @Test
    @DisplayName("INV-R02 삭제 이벤트를 받으면 자녀 복사본 행이 남고 그 이벤트가 일어난 시각이 삭제 시각으로 저장된다")
    void keepRowAndSaveDeletedAt() {
        LocalDateTime deletedAt = CHILD_CHANGED_AT.plusMinutes(5);
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        replicaChildService.applyChildChanged(RecommendFixture.childDeletedEvent(deletedAt));

        assertThat(replicaChildRepository.findById(CHILD_1_ID)).get()
            .extracting(RecommendReplicaChild::isDeleted, RecommendReplicaChild::getDeletedAt)
            .containsExactly(true, deletedAt);
    }

    @Test
    @DisplayName("INV-R02 삭제된 뒤 늦게 도착한 이전 수정 이벤트를 받아도 예외 없이 자녀 복사본이 삭제 상태 그대로다")
    void stayDeletedWhenEarlierEventArrivesLate() {
        LocalDateTime deletedAt = CHILD_CHANGED_AT.plusMinutes(5);
        replicaChildService.applyChildChanged(RecommendFixture.childDeletedEvent(deletedAt));

        assertThatCode(() -> replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventAt(new BigDecimal("120.0"), CHILD_CHANGED_AT)))
            .doesNotThrowAnyException();

        RecommendReplicaChild replica = replicaChildRepository.findById(CHILD_1_ID).orElseThrow();
        assertThat(replica.isDeleted()).isTrue();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("115.0");
        assertThat(replica.getEventOccurredAt()).isEqualTo(deletedAt);
    }
}
