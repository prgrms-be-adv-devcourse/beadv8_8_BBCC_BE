package com.bbcc.kidly.boundedcontext.recommend.out.growth;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRecommendGrowthRecordRepository
    extends JpaRepository<RecommendGrowthRecord, Long>, RecommendGrowthRecordRepository {
}
