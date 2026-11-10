package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.app.member.RecommendMemberWithdrawalService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.out.activity.JpaActivityRecordRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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

    private static final int RACE_REPETITIONS = 20;

    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaChildRepository replicaChildRepository;

    @Autowired
    private ReferenceGarmentRepository referenceGarmentRepository;

    @Autowired
    private RecommendMemberWithdrawalService memberWithdrawalService;

    // 남은 활동 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaActivityRecordRepository activityRecordRepository;

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

    @Test
    @DisplayName("회원 탈퇴를 두 번 받아도 예외 없이 삭제된 자녀를 포함한 그 회원 자녀의 기준 옷과 활동 기록만 지운다")
    void deleteGarmentsAndActivityRecordsOfWithdrawnMember() {
        givenTwoMembersWithGarmentsAndActivityRecords();
        var withdrawn = ActivityRecordFixture.memberWithdrawnEvent(MEMBER_1_ID);

        memberWithdrawalService.deleteDataOfWithdrawnMember(withdrawn);
        assertThatCode(() -> memberWithdrawalService.deleteDataOfWithdrawnMember(withdrawn))
            .doesNotThrowAnyException();

        assertThat(count(CHILD_1_ID)).isZero();
        assertThat(count(SIBLING_ID)).isZero();
        assertThat(count(OTHER_MEMBER_CHILD_ID)).isEqualTo(1);
        assertThat(activityRecordRepository.findAll()).extracting(ActivityRecord::getMemberId)
            .containsExactly(OTHER_MEMBER_ID);
    }

    @Test
    @DisplayName("회원 ID가 없는 탈퇴는 예외로 실패하고 기준 옷과 활동 기록을 지우지 않는다")
    void rejectWithdrawnWithoutMember() {
        givenTwoMembersWithGarmentsAndActivityRecords();

        assertThatThrownBy(() -> memberWithdrawalService.deleteDataOfWithdrawnMember(
            ActivityRecordFixture.memberWithdrawnEvent(null)))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");

        assertThat(count(CHILD_1_ID)).isEqualTo(2);
        assertThat(count(SIBLING_ID)).isEqualTo(1);
        assertThat(count(OTHER_MEMBER_CHILD_ID)).isEqualTo(1);
        assertThat(activityRecordRepository.findAll()).hasSize(2);
    }

    // 두 이벤트가 같은 줄을 지우려 겹치는 순간이 짧아서 한 번으로는 겹치지 않을 수 있으므로 여러 번 되풀이한다
    @RepeatedTest(RACE_REPETITIONS)
    @DisplayName("자녀 삭제와 그 회원의 탈퇴를 실제 스레드 두 개가 동시에 받아도 둘 다 예외 없이 끝나고 기준 옷이 남지 않는다")
    void deleteGarmentsOfChildAndMemberAtOnce() throws Exception {
        givenTwoMembersWithGarmentsAndActivityRecords();
        var childDeleted = RecommendFixture.childDeletedEvent(DELETED_AT);
        var withdrawn = ActivityRecordFixture.memberWithdrawnEvent(MEMBER_1_ID);

        List<Throwable> failures = runAtOnce(
            () -> replicaChildService.applyChildChanged(childDeleted),
            () -> memberWithdrawalService.deleteDataOfWithdrawnMember(withdrawn));

        assertThat(failures).isEmpty();
        assertThat(count(CHILD_1_ID)).isZero();
        assertThat(count(SIBLING_ID)).isZero();
        assertThat(count(OTHER_MEMBER_CHILD_ID)).isEqualTo(1);
    }

    // 회원 1은 자녀 1과 삭제된 형제, 다른 회원은 자녀 하나를 둔다. 회원마다 활동 기록이 하나씩 있다
    private void givenTwoMembersWithGarmentsAndActivityRecords() {
        replicaChildService.applyChildChanged(childChanged(CHILD_1_ID, MEMBER_1_ID, false));
        replicaChildService.applyChildChanged(childChanged(SIBLING_ID, MEMBER_1_ID, true));
        replicaChildService.applyChildChanged(childChanged(OTHER_MEMBER_CHILD_ID, OTHER_MEMBER_ID, false));
        save(ReferenceGarmentFixture.top(CHILD_1_ID));
        save(ReferenceGarmentFixture.bottom(CHILD_1_ID));
        save(ReferenceGarmentFixture.top(SIBLING_ID));
        save(ReferenceGarmentFixture.top(OTHER_MEMBER_CHILD_ID));
        activityRecordRepository.saveAndFlush(search(MEMBER_1_ID));
        activityRecordRepository.saveAndFlush(search(OTHER_MEMBER_ID));
    }

    private static ChildChangedEvent childChanged(Long childId, Long memberId, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), CHILD_CHANGED_AT,
            new ChildChangedEvent.Data(childId, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"),
                Gender.MALE, RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private static ActivityRecord search(Long memberId) {
        return ActivityRecord.searched(new ActivityRecord.SearchInfo(UUID.randomUUID(), memberId, "맨투맨", 52L,
            Map.of("COLOR", List.of(3L, 5L)), 12L, CHILD_CHANGED_AT));
    }

    private void save(ReferenceGarment garment) {
        transactionTemplate.executeWithoutResult(status -> referenceGarmentRepository.save(garment));
    }

    private long count(Long childProfileId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from recommend.reference_garment where child_profile_id = ?", Long.class, childProfileId);
    }

    // 작업마다 스레드를 하나씩 띄우고 모두 준비된 뒤 출발 신호 하나로 함께 시작한다. 예외로 끝난 작업의 예외를 모아 돌려준다
    private static List<Throwable> runAtOnce(Runnable... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    task.run();
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }
}
