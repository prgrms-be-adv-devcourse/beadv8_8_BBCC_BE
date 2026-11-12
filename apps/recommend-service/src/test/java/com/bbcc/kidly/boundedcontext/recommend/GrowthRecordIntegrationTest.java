package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.app.growth.RecommendGrowthRecordService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.JpaRecommendGrowthRecordRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 성장 기록 서비스가 실제 DB에서 자녀 성장 기록을 저장하는 규칙을 확인한다.
 * Kafka로 받는 연결은 RecommendGrowthKafkaIntegrationTest가 확인한다.
 */
@IntegrationTest
class GrowthRecordIntegrationTest {

    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    // 저장된 기록 전체를 읽으려고 JpaRepository의 기본 조회를 쓴다
    @Autowired
    private JpaRecommendGrowthRecordRepository growthRecordRepository;

    @Autowired
    private RecommendGrowthRecordService growthRecordService;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    // 체형은 입력하지 않을 수 있어 null도 그대로 저장한다
    @ParameterizedTest(name = "체형 {0}")
    @NullSource
    @EnumSource(value = BodyType.class, names = "SLIM")
    @DisplayName("성장 기록 이벤트의 기록 ID · 자녀 · 키 · 체형 · 기록 시각을 그대로 저장한다")
    void saveGrowthRecord(BodyType bodyType) {
        growthRecordService.recordGrowth(growthRecorded(501L, new BigDecimal("115.5"), bodyType, RECORDED_AT));

        assertThat(growthRecordRepository.findAll()).singleElement()
            .satisfies(record -> {
                assertThat(record.getGrowthRecordId()).isEqualTo(501L);
                assertThat(record.getChildProfileId()).isEqualTo(CHILD_1_ID);
                assertThat(record.getHeightCm()).isEqualByComparingTo("115.5");
                assertThat(record.getBodyType()).isEqualTo(bodyType);
                assertThat(record.getRecordedAt()).isEqualTo(RECORDED_AT);
            });
    }

    @Test
    @DisplayName("이미 삭제된 자녀의 성장 기록은 예외 없이 저장하지 않는다")
    void skipGrowthRecordOfDeletedChild() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        replicaChildService.applyChildChanged(RecommendFixture.childDeletedEvent(CHILD_CHANGED_AT.plusMinutes(5)));

        assertThatCode(() -> growthRecordService.recordGrowth(
            growthRecorded(501L, new BigDecimal("115.5"), BodyType.SLIM, RECORDED_AT)))
            .doesNotThrowAnyException();

        assertThat(growthRecordRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("같은 성장 기록을 다시 받아도 예외 없이 기록은 한 줄이다")
    void keepOneRecordForSameGrowthRecord() {
        growthRecordService.recordGrowth(growthRecorded(501L, new BigDecimal("115.5"), BodyType.SLIM, RECORDED_AT));

        // 다시 보낸 이벤트는 eventId만 새 값이다
        assertThatCode(() -> growthRecordService.recordGrowth(
            growthRecorded(501L, new BigDecimal("115.5"), BodyType.SLIM, RECORDED_AT)))
            .doesNotThrowAnyException();

        assertThat(growthRecordRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 자녀의 키를 고친 기록은 앞 기록을 덮지 않고 새 줄로 쌓인다")
    void stackRecordsOfSameChild() {
        growthRecordService.recordGrowth(growthRecorded(501L, new BigDecimal("115.5"), BodyType.NORMAL, RECORDED_AT));
        growthRecordService.recordGrowth(
            growthRecorded(502L, new BigDecimal("116.0"), BodyType.NORMAL, RECORDED_AT.plusDays(30)));

        assertThat(growthRecordRepository.findAll())
            .extracting(RecommendGrowthRecord::getGrowthRecordId, RecommendGrowthRecord::getHeightCm)
            .containsExactlyInAnyOrder(
                tuple(501L, new BigDecimal("115.5")),
                tuple(502L, new BigDecimal("116.0")));
    }

    // 자녀 1의 성장 기록 이벤트다. 발생 시각은 기록 시각과 같다
    private static ChildGrowthRecordedEvent growthRecorded(Long growthRecordId, BigDecimal heightCm,
        BodyType bodyType, LocalDateTime recordedAt) {
        return new ChildGrowthRecordedEvent(UUID.randomUUID(), recordedAt,
            new ChildGrowthRecordedEvent.Data(growthRecordId, CHILD_1_ID, heightCm, bodyType, recordedAt));
    }
}
