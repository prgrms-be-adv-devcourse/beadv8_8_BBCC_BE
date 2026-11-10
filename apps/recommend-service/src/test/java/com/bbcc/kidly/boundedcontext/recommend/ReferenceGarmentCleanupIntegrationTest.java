package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀 삭제와 회원 탈퇴를 받았을 때 기준 옷이 남지 않는지 서비스를 직접 불러 확인한다.
 * Kafka로 받는 연결은 RecommendKafkaListenerIntegrationTest와 RecommendMemberKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class ReferenceGarmentCleanupIntegrationTest {

    private static final Long SIBLING_ID = 12L;

    private static final Long OTHER_MEMBER_CHILD_ID = 21L;

    private static final LocalDateTime DELETED_AT = CHILD_CHANGED_AT.plusMinutes(5);

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaChildRepository replicaChildRepository;

    @Autowired
    private ReferenceGarmentRepository referenceGarmentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("자녀 삭제를 받으면 그 자녀의 상의 · 하의 기준 옷만 지우고 복사본에는 삭제 표시를 한다")
    void deleteGarmentsOfDeletedChild() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        save(ReferenceGarmentFixture.top(CHILD_1_ID));
        save(ReferenceGarmentFixture.bottom(CHILD_1_ID));
        save(ReferenceGarmentFixture.top(SIBLING_ID));
        save(ReferenceGarmentFixture.top(OTHER_MEMBER_CHILD_ID));

        replicaChildService.applyChildChanged(RecommendFixture.childDeletedEvent(DELETED_AT));

        assertThat(count(CHILD_1_ID)).isZero();
        assertThat(count(SIBLING_ID)).isEqualTo(1);
        assertThat(count(OTHER_MEMBER_CHILD_ID)).isEqualTo(1);
        assertThat(replicaChildRepository.findById(CHILD_1_ID)).get()
            .extracting(RecommendReplicaChild::isDeleted)
            .isEqualTo(true);
    }

    @Test
    @DisplayName("같은 자녀 삭제를 두 번 받아도 예외 없이 기준 옷이 없고 복사본의 삭제 시각은 처음 그대로다")
    void acceptSameChildDeletedTwice() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        save(ReferenceGarmentFixture.top(CHILD_1_ID));
        var deleted = RecommendFixture.childDeletedEvent(DELETED_AT);

        replicaChildService.applyChildChanged(deleted);
        assertThatCode(() -> replicaChildService.applyChildChanged(deleted)).doesNotThrowAnyException();

        assertThat(count(CHILD_1_ID)).isZero();
        assertThat(replicaChildRepository.findById(CHILD_1_ID)).get()
            .extracting(RecommendReplicaChild::isDeleted, RecommendReplicaChild::getDeletedAt)
            .containsExactly(true, DELETED_AT);
    }

    @Test
    @DisplayName("복사본에 반영한 시각보다 이르게 일어난 삭제가 늦게 도착해도 그 자녀의 기준 옷은 지운다")
    void deleteGarmentsOnLateChildDeleted() {
        replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventAt(new BigDecimal("120.0"), DELETED_AT.plusMinutes(10)));
        save(ReferenceGarmentFixture.top(CHILD_1_ID));
        save(ReferenceGarmentFixture.bottom(CHILD_1_ID));

        assertThatCode(() -> replicaChildService.applyChildChanged(RecommendFixture.childDeletedEvent(DELETED_AT)))
            .doesNotThrowAnyException();

        assertThat(count(CHILD_1_ID)).isZero();
    }

    @Test
    @DisplayName("삭제가 아닌 자녀 정보 변경을 받으면 기준 옷은 그대로다")
    void keepGarmentsOnChildChanged() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        save(ReferenceGarmentFixture.top(CHILD_1_ID));
        save(ReferenceGarmentFixture.bottom(CHILD_1_ID));

        replicaChildService.applyChildChanged(
            RecommendFixture.childChangedEventAt(new BigDecimal("120.0"), CHILD_CHANGED_AT.plusMinutes(1)));

        assertThat(count(CHILD_1_ID)).isEqualTo(2);
    }

    private void save(ReferenceGarment garment) {
        transactionTemplate.executeWithoutResult(status -> referenceGarmentRepository.save(garment));
    }

    private long count(Long childProfileId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from recommend.reference_garment where child_profile_id = ?", Long.class, childProfileId);
    }
}
