package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.ReferenceGarmentResult;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceGarmentService {

    private final ReferenceGarmentRepository referenceGarmentRepository;

    @Transactional(readOnly = true)
    public List<ReferenceGarmentResult> list(Long memberId, Long childId) {
        return referenceGarmentRepository.findAllByChildProfileId(childId).stream()
            .map(ReferenceGarmentResult::from)
            .toList();
    }
}
