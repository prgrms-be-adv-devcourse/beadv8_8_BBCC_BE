package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자녀별 기준 옷을 실제 DB에 저장하고 읽고 지우는 규칙을 확인한다. 리포지토리는 트랜잭션을 열지 않으므로 호출은 TransactionTemplate으로 감싼다.
 */
@IntegrationTest
class ReferenceGarmentRepositoryIntegrationTest {

    private static final Long CHILD_ID = 11L;

    private static final Long OTHER_CHILD_ID = 12L;

    private static final Long DELETED_CHILD_ID = 13L;

    private static final Long MEMBER_ID = RecommendFixture.MEMBER_1_ID;

    private static final Long OTHER_MEMBER_ID = RecommendFixture.OTHER_MEMBER_ID;

    @Autowired
    private ReferenceGarmentRepository referenceGarmentRepository;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("소수 첫째 자리 실측과 선택 실측까지 저장하고 다시 읽으면 값이 그대로다")
    void roundTripMeasurements() {
        Map<String, BigDecimal> measurements = Map.of(
            "TOTAL_LENGTH", new BigDecimal("40.5"), "CHEST_WIDTH", new BigDecimal("42.0"),
            "SHOULDER_WIDTH", new BigDecimal("35.0"), "SLEEVE_LENGTH", new BigDecimal("30.5"));
        save(CHILD_ID, topSpec(measurements));

        ReferenceGarment found = find(CHILD_ID, GarmentPart.TOP);

        assertThat(found.getMeasurements()).isEqualTo(measurements);
        assertThat(found.getCategoryId()).isEqualTo(52L);
        assertThat(found.getPart()).isEqualTo(GarmentPart.TOP);
    }

    @Test
    @DisplayName("읽어 온 기준 옷의 실측은 밖에서 바꿀 수 없다")
    void foundMeasurementsAreReadOnly() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        ReferenceGarment found = find(CHILD_ID, GarmentPart.TOP);

        assertThatThrownBy(() -> found.getMeasurements().put("TOTAL_LENGTH", BigDecimal.ONE))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("같은 자녀의 같은 구분을 새로 한 번 더 저장하면 UNIQUE 제약이 막는다")
    void rejectSecondRowOfSameChildAndPart() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        assertThatThrownBy(() -> save(CHILD_ID, topSpec(topMeasurements("41.0", "43.0"))))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count(CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 자녀의 다른 구분과 다른 자녀의 같은 구분은 각각 저장된다")
    void saveOtherPartAndOtherChild() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        save(CHILD_ID, bottomSpec());
        save(OTHER_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        assertThat(count(CHILD_ID)).isEqualTo(2);
        assertThat(count(OTHER_CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("찾은 기준 옷을 바꾸고 커밋하면 줄은 하나이고 값과 수정 시각이 바뀐다")
    void replaceFoundGarmentInPlace() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));
        LocalDateTime updatedBefore = jdbcTemplate.queryForObject(
            "select updated_at from recommend.reference_garment where child_profile_id = ?",
            LocalDateTime.class, CHILD_ID);

        transactionTemplate.executeWithoutResult(status -> referenceGarmentRepository
            .findByChildProfileIdAndPart(CHILD_ID, GarmentPart.TOP).orElseThrow()
            .replace(topSpec(topMeasurements("44.0", "45.5"))));

        assertThat(count(CHILD_ID)).isEqualTo(1);
        assertThat(find(CHILD_ID, GarmentPart.TOP).getMeasurements()).isEqualTo(topMeasurements("44.0", "45.5"));
        LocalDateTime updatedAfter = jdbcTemplate.queryForObject(
            "select updated_at from recommend.reference_garment where child_profile_id = ?",
            LocalDateTime.class, CHILD_ID);
        assertThat(updatedAfter).isAfter(updatedBefore);
    }

    @Test
    @DisplayName("하의를 먼저 저장해도 자녀의 기준 옷은 상의, 하의 순서로 돌아오고 다른 자녀의 것은 섞이지 않는다")
    void listTopBeforeBottomOfOneChild() {
        save(CHILD_ID, bottomSpec());
        save(OTHER_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        List<ReferenceGarment> garments = transactionTemplate.execute(
            status -> referenceGarmentRepository.findAllByChildProfileId(CHILD_ID));

        assertThat(garments).extracting(ReferenceGarment::getPart)
            .containsExactly(GarmentPart.TOP, GarmentPart.BOTTOM);
        assertThat(garments).extracting(ReferenceGarment::getChildProfileId).containsOnly(CHILD_ID);
    }

    @Test
    @DisplayName("구분 하나를 지우면 그 줄만 지워지고 같은 자녀의 다른 구분과 다른 자녀의 것은 남는다")
    void deleteOnlyThatPart() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));
        save(CHILD_ID, bottomSpec());
        save(OTHER_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        transactionTemplate.executeWithoutResult(
            status -> referenceGarmentRepository.deleteByChildProfileIdAndPart(CHILD_ID, GarmentPart.TOP));

        assertThat(count(CHILD_ID)).isEqualTo(1);
        assertThat(find(CHILD_ID, GarmentPart.BOTTOM).getPart()).isEqualTo(GarmentPart.BOTTOM);
        assertThat(count(OTHER_CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 기준 옷을 지워도 예외가 없고 다른 줄은 그대로다")
    void deleteMissingGarmentWithoutError() {
        save(OTHER_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        transactionTemplate.executeWithoutResult(
            status -> referenceGarmentRepository.deleteByChildProfileIdAndPart(CHILD_ID, GarmentPart.TOP));

        assertThat(count(OTHER_CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("자녀를 지우면 그 자녀의 두 줄만 지워지고 다른 자녀의 것은 남는다")
    void deleteAllOfChild() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));
        save(CHILD_ID, bottomSpec());
        save(OTHER_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        transactionTemplate.executeWithoutResult(
            status -> referenceGarmentRepository.deleteByChildProfileId(CHILD_ID));

        assertThat(count(CHILD_ID)).isZero();
        assertThat(count(OTHER_CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("회원의 기준 옷을 지우면 삭제된 자녀를 포함한 그 회원의 자녀 것만 지워지고 다른 회원 자녀의 것은 남는다")
    void deleteAllOfMemberChildren() {
        replicaChildService.applyChildChanged(childChanged(CHILD_ID, MEMBER_ID, false));
        replicaChildService.applyChildChanged(childChanged(DELETED_CHILD_ID, MEMBER_ID, true));
        replicaChildService.applyChildChanged(childChanged(OTHER_CHILD_ID, OTHER_MEMBER_ID, false));
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));
        save(CHILD_ID, bottomSpec());
        save(DELETED_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));
        save(OTHER_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        transactionTemplate.executeWithoutResult(
            status -> referenceGarmentRepository.deleteAllOfMember(MEMBER_ID));

        assertThat(count(CHILD_ID)).isZero();
        assertThat(count(DELETED_CHILD_ID)).isZero();
        assertThat(count(OTHER_CHILD_ID)).isEqualTo(1);
    }

    private static ChildChangedEvent childChanged(Long childId, Long memberId, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), RecommendFixture.CHILD_CHANGED_AT,
            new ChildChangedEvent.Data(childId, memberId, new BigDecimal("115.0"), new BigDecimal("20.0"),
                Gender.MALE, RecommendFixture.CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, deleted));
    }

    private void save(Long childProfileId, ReferenceGarmentSpec spec) {
        transactionTemplate.executeWithoutResult(
            status -> referenceGarmentRepository.save(ReferenceGarment.create(childProfileId, spec)));
    }

    private ReferenceGarment find(Long childProfileId, GarmentPart part) {
        return transactionTemplate.execute(status ->
            referenceGarmentRepository.findByChildProfileIdAndPart(childProfileId, part).orElseThrow());
    }

    private long count(Long childProfileId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from recommend.reference_garment where child_profile_id = ?", Long.class, childProfileId);
    }

    private static Map<String, BigDecimal> topMeasurements(String totalLength, String chestWidth) {
        return Map.of("TOTAL_LENGTH", new BigDecimal(totalLength), "CHEST_WIDTH", new BigDecimal(chestWidth));
    }

    private static ReferenceGarmentSpec topSpec(Map<String, BigDecimal> measurements) {
        return ReferenceGarmentSpec.of(GarmentPart.TOP, 52L,
            List.of(new GarmentCategoryPathEntry(1L, "의류"), new GarmentCategoryPathEntry(52L, "상의")),
            measurements);
    }

    private static ReferenceGarmentSpec bottomSpec() {
        return ReferenceGarmentSpec.of(GarmentPart.BOTTOM, 60L,
            List.of(new GarmentCategoryPathEntry(1L, "의류"), new GarmentCategoryPathEntry(60L, "하의")),
            Map.of("TOTAL_LENGTH", new BigDecimal("60.0"), "WAIST_WIDTH", new BigDecimal("30.0")));
    }
}
