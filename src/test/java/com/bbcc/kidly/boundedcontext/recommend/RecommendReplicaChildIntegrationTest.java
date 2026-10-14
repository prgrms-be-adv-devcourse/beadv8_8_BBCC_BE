package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_BIRTH_DATE;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.global.eventpublisher.EventPublisher;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
