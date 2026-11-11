package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.growth.GrowthRecordService;
import com.bbcc.kidly.boundedcontext.recommend.out.growth.JpaRecommendGrowthRecordRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    private GrowthRecordService growthRecordService;

    @Test
    @DisplayName("성장 기록 이벤트의 기록 ID · 자녀 · 키 · 체형 · 기록 시각을 그대로 저장한다")
    void saveGrowthRecord() {
        growthRecordService.recordGrowth(growthRecorded(501L, new BigDecimal("115.5"), BodyType.SLIM, RECORDED_AT));

        assertThat(growthRecordRepository.findAll()).singleElement()
            .satisfies(record -> {
                assertThat(record.getGrowthRecordId()).isEqualTo(501L);
                assertThat(record.getChildProfileId()).isEqualTo(CHILD_1_ID);
                assertThat(record.getHeightCm()).isEqualByComparingTo("115.5");
                assertThat(record.getBodyType()).isEqualTo(BodyType.SLIM);
                assertThat(record.getRecordedAt()).isEqualTo(RECORDED_AT);
            });
    }

    // 자녀 1의 성장 기록 이벤트다. 발생 시각은 기록 시각과 같다
    private static ChildGrowthRecordedEvent growthRecorded(Long growthRecordId, BigDecimal heightCm,
        BodyType bodyType, LocalDateTime recordedAt) {
        return new ChildGrowthRecordedEvent(UUID.randomUUID(), recordedAt,
            new ChildGrowthRecordedEvent.Data(growthRecordId, CHILD_1_ID, heightCm, bodyType, recordedAt));
    }
}
