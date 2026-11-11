package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

public interface RecommendGrowthRecordRepository {

    RecommendGrowthRecord save(RecommendGrowthRecord growthRecord);

    boolean existsById(Long growthRecordId);

    void deleteByChildProfileId(Long childProfileId);
}
