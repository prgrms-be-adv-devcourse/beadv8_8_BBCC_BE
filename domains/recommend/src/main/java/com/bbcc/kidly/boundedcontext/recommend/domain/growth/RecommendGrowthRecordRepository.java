package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import java.time.LocalDateTime;
import java.util.List;

public interface RecommendGrowthRecordRepository {

    RecommendGrowthRecord save(RecommendGrowthRecord growthRecord);

    boolean existsById(Long growthRecordId);

    void deleteByGrowthRecordId(Long growthRecordId);

    /**
     * 그 자녀가 since 시각부터 잰 성장 기록을 돌려준다. 성장 속도는 최근 기록만 쓰므로 오래된 기록은 읽지 않는다.
     */
    List<RecommendGrowthRecord> findRecordedSince(Long childProfileId, LocalDateTime since);

    void deleteByChildProfileId(Long childProfileId);

    void deleteAllOfMember(Long memberId);
}
