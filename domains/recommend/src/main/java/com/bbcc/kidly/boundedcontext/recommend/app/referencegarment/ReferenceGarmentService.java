package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment.GarmentCategoryPaths;
import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.ReferenceGarmentResult;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
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

    @Transactional(readOnly = true)
    public List<ReferenceGarmentResult> list(Long memberId, Long childId) {
        return referenceGarmentRepository.findAllByChildProfileId(childId).stream()
            .map(ReferenceGarmentResult::from)
            .toList();
    }

    @Transactional
    public ReferenceGarmentResult save(Long memberId, Long childId, String partName, Long categoryId,
        Map<String, BigDecimal> measurements) {
        GarmentPart part = GarmentPart.valueOf(partName);
        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(
            part, categoryId, garmentCategoryPaths.findPathOf(categoryId), measurements);
        Optional<ReferenceGarment> existing = referenceGarmentRepository.findByChildProfileIdAndPart(childId, part);
        if (existing.isPresent()) {
            existing.get().replace(spec);
            return ReferenceGarmentResult.from(existing.get());
        }
        return ReferenceGarmentResult.from(referenceGarmentRepository.save(ReferenceGarment.create(childId, spec)));
    }
}
