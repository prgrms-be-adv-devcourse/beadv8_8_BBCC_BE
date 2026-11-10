package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment.GarmentCategoryPaths;
import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.ReferenceGarmentResult;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceGarmentService {

    private final ReferenceGarmentRepository referenceGarmentRepository;

    private final GarmentCategoryPaths garmentCategoryPaths;

    private final RecommendReplicaChildRepository replicaChildRepository;

    @Transactional(readOnly = true)
    public List<ReferenceGarmentResult> list(Long memberId, Long childId) {
        requireChildOf(memberId, childId);
        return referenceGarmentRepository.findAllByChildProfileId(childId).stream()
            .map(ReferenceGarmentResult::from)
            .toList();
    }

    @Transactional
    public ReferenceGarmentResult save(Long memberId, Long childId, String partName, Long categoryId,
        Map<String, BigDecimal> measurements) {
        GarmentPart part = GarmentPart.from(partName);
        requireChildOf(memberId, childId);
        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(
            part, categoryId, garmentCategoryPaths.findPathOf(categoryId), measurements);
        Optional<ReferenceGarment> existing = referenceGarmentRepository.findByChildProfileIdAndPart(childId, part);
        if (existing.isPresent()) {
            existing.get().replace(spec);
            return ReferenceGarmentResult.from(existing.get());
        }
        return ReferenceGarmentResult.from(referenceGarmentRepository.save(ReferenceGarment.create(childId, spec)));
    }

    @Transactional
    public void delete(Long memberId, Long childId, String partName) {
        GarmentPart part = GarmentPart.from(partName);
        requireChildOf(memberId, childId);
        referenceGarmentRepository.deleteByChildProfileIdAndPart(childId, part);
    }

    // 사이즈 추천은 남의 자녀도 없는 자녀처럼 404로 답하지만, 이 API는 남의 자녀를 403으로 구분한다
    private void requireChildOf(Long memberId, Long childId) {
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (child.isDeleted()) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(ReferenceGarmentErrorCode.CHILD_FORBIDDEN);
        }
    }
}
