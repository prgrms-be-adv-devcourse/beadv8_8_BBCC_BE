package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import java.util.List;
import java.util.Optional;

public interface ReferenceGarmentRepository {

    ReferenceGarment save(ReferenceGarment garment);

    Optional<ReferenceGarment> findByChildProfileIdAndPart(Long childProfileId, GarmentPart part);

    /**
     * 그 자녀의 기준 옷을 상의, 하의 순서로 돌려준다.
     */
    List<ReferenceGarment> findAllByChildProfileId(Long childProfileId);

    void deleteByChildProfileIdAndPart(Long childProfileId, GarmentPart part);

    void deleteByChildProfileId(Long childProfileId);
}
