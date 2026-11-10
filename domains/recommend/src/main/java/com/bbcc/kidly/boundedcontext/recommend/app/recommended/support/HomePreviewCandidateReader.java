package com.bbcc.kidly.boundedcontext.recommend.app.recommended.support;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductRanking;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.HomePreviewSelector;
import com.bbcc.kidly.boundedcontext.recommend.domain.recommended.RecommendedProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 맛보기 후보를 읽는다. 후보는 자녀에게 맞는 사이즈의 상품을 선호 점수 순서로 세운 앞 20개다.
 */
@Component
public class HomePreviewCandidateReader {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final RecommendReplicaChildRepository replicaChildRepository;
    private final RecommendedProductRanking ranking;
    private final HomePreviewSelector selector = new HomePreviewSelector();

    public HomePreviewCandidateReader(
        RecommendReplicaChildRepository replicaChildRepository, RecommendedProductRanking ranking) {
        this.replicaChildRepository = replicaChildRepository;
        this.ranking = ranking;
    }

    // 상품의 사이즈 줄은 지연 로딩이므로 트랜잭션 안에서 읽어 계산한다
    @Transactional(readOnly = true)
    public List<RecommendedProduct> read(Long memberId, Long childId) {
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }
        LocalDateTime asOf = LocalDateTime.now(SERVICE_ZONE).truncatedTo(ChronoUnit.SECONDS);
        return selector.candidates(ranking.rank(memberId, child, null, asOf));
    }
}
