package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private ReferenceGarmentRepository referenceGarmentRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("FIT-01 소수 첫째 자리 실측과 선택 실측까지 저장하고 다시 읽으면 값이 그대로다")
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
    @DisplayName("FIT-01 같은 자녀의 같은 구분을 새로 한 번 더 저장하면 UNIQUE 제약이 막는다")
    void rejectSecondRowOfSameChildAndPart() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        assertThatThrownBy(() -> save(CHILD_ID, topSpec(topMeasurements("41.0", "43.0"))))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count(CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("FIT-01 같은 자녀의 다른 구분과 다른 자녀의 같은 구분은 각각 저장된다")
    void saveOtherPartAndOtherChild() {
        save(CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        save(CHILD_ID, bottomSpec());
        save(OTHER_CHILD_ID, topSpec(topMeasurements("40.5", "42.0")));

        assertThat(count(CHILD_ID)).isEqualTo(2);
        assertThat(count(OTHER_CHILD_ID)).isEqualTo(1);
    }

    @Test
    @DisplayName("FIT-01 찾은 기준 옷을 바꾸고 커밋하면 줄은 하나이고 값과 수정 시각이 바뀐다")
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
