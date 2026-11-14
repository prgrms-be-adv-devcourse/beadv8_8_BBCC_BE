package com.bbcc.kidly.boundedcontext.recommend.app.growth.support;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 자녀의 성장 기록을 읽어 키 성장 속도를 계산한다. 사이즈 추천과 성장 속도 조회가 같은 값을 내도록 함께 쓴다.
 * 트랜잭션은 부르는 쪽이 연다.
 */
@Component
public class GrowthVelocityReader {

    private final RecommendGrowthRecordRepository growthRecordRepository;
    private final GrowthVelocityCalculator growthVelocityCalculator;

    public GrowthVelocityReader(
        RecommendGrowthRecordRepository growthRecordRepository, GrowthVelocityTable growthVelocityTable) {
        this.growthRecordRepository = growthRecordRepository;
        this.growthVelocityCalculator = new GrowthVelocityCalculator(growthVelocityTable);
    }

    // 성장 속도 계산이 보는 기간 안의 이 자녀 기록만 읽는다
    public GrowthVelocity read(RecommendReplicaChild child, LocalDate today) {
        LocalDateTime since = today.minusMonths(GrowthVelocityCalculator.WINDOW_MONTHS).atStartOfDay();
        List<RecommendGrowthRecord> records =
            growthRecordRepository.findRecordedSince(child.getChildProfileId(), since);
        return growthVelocityCalculator.calculate(child.getGender(), child.getBirthDate(), records, today);
    }
}
