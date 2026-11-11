package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.OTHER_MEMBER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bbcc.kidly.boundedcontext.recommend.app.growth.GrowthRecordService;
import com.bbcc.kidly.boundedcontext.recommend.app.member.RecommendMemberWithdrawalService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 자녀 삭제와 회원 탈퇴를 받으면 그 자녀의 성장 기록을 지우는지 실제 DB에서 확인한다.
 */
@IntegrationTest
class GrowthRecordCleanupIntegrationTest {

    private static final Long SIBLING_ID = 12L;

    private static final Long OTHER_MEMBER_CHILD_ID = 21L;

    private static final LocalDateTime DELETED_AT = CHILD_CHANGED_AT.plusMinutes(5);

    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private GrowthRecordService growthRecordService;

    @Autowired
    private RecommendMemberWithdrawalService memberWithdrawalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("자녀 삭제를 받으면 그 자녀의 성장 기록만 지우고 다른 자녀의 기록은 남긴다")
    void deleteGrowthRecordsOfDeletedChild() {
        givenTwoMembersWithGrowthRecords();

        replicaChildService.applyChildChanged(RecommendFixture.childDeletedEvent(DELETED_AT));

        assertThat(count(CHILD_1_ID)).isZero();
        assertThat(count(SIBLING_ID)).isEqualTo(1);
        assertThat(count(OTHER_MEMBER_CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("회원 탈퇴를 두 번 받아도 예외 없이 삭제된 자녀를 포함한 그 회원 자녀의 성장 기록만 지운다")
    void deleteGrowthRecordsOfWithdrawnMember() {
        givenTwoMembersWithGrowthRecords();
        var withdrawn = ActivityRecordFixture.memberWithdrawnEvent(MEMBER_1_ID);

        memberWithdrawalService.deleteDataOfWithdrawnMember(withdrawn);
        assertThatCode(() -> memberWithdrawalService.deleteDataOfWithdrawnMember(withdrawn))
            .doesNotThrowAnyException();

        assertThat(count(CHILD_1_ID)).isZero();
        assertThat(count(SIBLING_ID)).isZero();
        assertThat(count(OTHER_MEMBER_CHILD_ID)).isEqualTo(1);
    }

    // 회원 1의 자녀 1(기록 둘)과 삭제된 형제(기록 하나), 회원 2의 자녀(기록 하나)를 둔다.
    // 형제의 기록은 삭제 표시 뒤에 저장해, 회원 탈퇴가 삭제된 자녀의 기록까지 찾는지 본다
    private void givenTwoMembersWithGrowthRecords() {
        replicaChildService.applyChildChanged(childChanged(CHILD_1_ID, MEMBER_1_ID, false));
        replicaChildService.applyChildChanged(childChanged(SIBLING_ID, MEMBER_1_ID, true));
        replicaChildService.applyChildChanged(childChanged(OTHER_MEMBER_CHILD_ID, OTHER_MEMBER_ID, false));
        growthRecordService.recordGrowth(growthRecorded(501L, CHILD_1_ID));
        growthRecordService.recordGrowth(growthRecorded(502L, CHILD_1_ID));
        growthRecordService.recordGrowth(growthRecorded(503L, SIBLING_ID));
        growthRecordService.recordGrowth(growthRecorded(504L, OTHER_MEMBER_CHILD_ID));
    }

    private static ChildChangedEvent childChanged(Long childId, Long memberId, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), CHILD_CHANGED_AT,
            new ChildChangedEvent.Data(childId, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"),
                Gender.MALE, RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private static ChildGrowthRecordedEvent growthRecorded(Long growthRecordId, Long childId) {
        return new ChildGrowthRecordedEvent(UUID.randomUUID(), RECORDED_AT,
            new ChildGrowthRecordedEvent.Data(growthRecordId, childId, new BigDecimal("115.0"), BodyType.NORMAL,
                RECORDED_AT));
    }

    private long count(Long childProfileId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from recommend.growth_record where child_profile_id = ?", Long.class, childProfileId);
    }
}
