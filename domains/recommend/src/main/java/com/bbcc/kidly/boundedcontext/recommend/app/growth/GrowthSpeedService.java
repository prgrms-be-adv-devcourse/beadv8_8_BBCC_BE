package com.bbcc.kidly.boundedcontext.recommend.app.growth;

import com.bbcc.kidly.boundedcontext.recommend.app.growth.dto.GrowthSpeedResult;
import com.bbcc.kidly.boundedcontext.recommend.app.growth.support.GrowthVelocityReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자녀의 키 성장 속도를 상품 없이 조회한다.
 */
@Service
@RequiredArgsConstructor
public class GrowthSpeedService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RecommendReplicaChildRepository replicaChildRepository;

    private final GrowthVelocityReader growthVelocityReader;

    // 사이즈 추천처럼 남의 자녀와 삭제된 자녀도 없는 자녀와 같이 404로 답한다
    @Transactional(readOnly = true)
    public GrowthSpeedResult find(Long memberId, Long childId) {
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }
        return GrowthSpeedResult.from(growthVelocityReader.read(child, LocalDate.now(SERVICE_ZONE)));
    }
}
