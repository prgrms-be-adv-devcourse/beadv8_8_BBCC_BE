package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_BIRTH_DATE;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀 정보 변경됨을 받아 자녀 복사본에 반영하는 흐름을 확인한다.
 * 리스너는 발행한 트랜잭션이 커밋된 뒤에 실행되므로 테스트에 @Transactional을 붙이지 않고 발행만 트랜잭션 안에서 한다.
 */
@IntegrationTest
class RecommendReplicaChildIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RecommendReplicaChildRepository replicaChildRepository;

    // 리스너의 예외는 커밋 뒤라 로그로만 남으므로, 반영 규칙은 서비스를 직접 불러 예외까지 확인한다
    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Test
    @DisplayName("자녀 정보 변경됨을 발행한 트랜잭션이 커밋되면 이벤트 값이 자녀 복사본에 저장된다")
    void saveReplicaAfterCommit() {
        transactionTemplate.executeWithoutResult(
            status -> eventPublisher.publish(RecommendFixture.childChangedEvent()));

        RecommendReplicaChild replica = replicaChildRepository.findById(CHILD_1_ID).orElseThrow();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("115.0");
        assertThat(replica.getWeightKg()).isEqualByComparingTo("20.0");
        assertThat(replica)
            .extracting(
                RecommendReplicaChild::getMemberId,
                RecommendReplicaChild::getGender,
                RecommendReplicaChild::getBirthDate,
                RecommendReplicaChild::getBodyType,
                RecommendReplicaChild::getPreferredFit,
                RecommendReplicaChild::getEventOccurredAt,
                RecommendReplicaChild::isDeleted)
            .containsExactly(MEMBER_1_ID, Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE,
                CHILD_CHANGED_AT, false);
    }

    @Test
    @DisplayName("자녀 정보 변경됨을 발행한 트랜잭션이 롤백되면 자녀 복사본이 생기지 않는다")
    void noReplicaAfterRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(RecommendFixture.childChangedEvent());
            status.setRollbackOnly();
        });

        assertThat(replicaChildRepository.findById(CHILD_1_ID)).isEmpty();
    }

    @Test
    @DisplayName("자녀 복사본 반영이 실패해도 발행한 트랜잭션은 성공하고 복사본은 이전 값 그대로다")
    void keepReplicaWhenListenerFails() {
        publishInTransaction(RecommendFixture.childChangedEvent());
        ChildChangedEvent withoutHeight = new ChildChangedEvent(UUID.randomUUID(), CHILD_CHANGED_AT.plusMinutes(1),
            new ChildChangedEvent.Data(CHILD_1_ID, MEMBER_1_ID, null, new BigDecimal("20.0"), Gender.MALE,
                CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, false));

        assertThatCode(() -> publishInTransaction(withoutHeight)).doesNotThrowAnyException();

        RecommendReplicaChild replica = replicaChildRepository.findById(CHILD_1_ID).orElseThrow();
        assertThat(replica.getHeightCm()).isEqualByComparingTo("115.0");
        assertThat(replica.getEventOccurredAt()).isEqualTo(CHILD_CHANGED_AT);
    }

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

    private void publishInTransaction(ChildChangedEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }
}
