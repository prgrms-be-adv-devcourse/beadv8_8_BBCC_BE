package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사이즈 추천이 성장 속도를 계산할 때 자녀 한 명의 최근 기록만 읽는지 실제 DB에서 확인한다.
 */
@IntegrationTest
class GrowthRecordSinceIntegrationTest {

    private static final Long OTHER_CHILD_ID = 12L;

    // 12개월 전 오늘 0시다. 이 시각에 잰 기록부터 읽는다
    private static final LocalDateTime SINCE = LocalDateTime.of(2025, 11, 12, 0, 0);

    @Autowired
    private RecommendGrowthRecordRepository growthRecordRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("GROW-01 성장 기록은 그 자녀가 기준 시각부터 잰 기록만 읽고, 기준 시각 직전 기록과 다른 자녀의 기록은 읽지 않는다")
    void readOnlyThatChildsRecordsSince() {
        save(1L, CHILD_1_ID, SINCE.minusSeconds(1));
        save(2L, CHILD_1_ID, SINCE);
        save(3L, CHILD_1_ID, SINCE.plusMonths(6));
        save(4L, OTHER_CHILD_ID, SINCE.plusMonths(6));

        List<RecommendGrowthRecord> records = transactionTemplate.execute(
            status -> growthRecordRepository.findRecordedSince(CHILD_1_ID, SINCE));

        assertThat(records).extracting(RecommendGrowthRecord::getGrowthRecordId).containsExactlyInAnyOrder(2L, 3L);
    }

    private void save(Long growthRecordId, Long childProfileId, LocalDateTime recordedAt) {
        transactionTemplate.executeWithoutResult(status -> growthRecordRepository.save(RecommendGrowthRecord.create(
            new RecommendGrowthRecord.RecordInfo(growthRecordId, childProfileId, new BigDecimal("115.0"), null,
                recordedAt))));
    }
}
