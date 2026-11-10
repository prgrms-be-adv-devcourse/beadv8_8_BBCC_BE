package com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment;

import java.util.Optional;

public interface ReferenceGarmentRepository {

    ReferenceGarment save(ReferenceGarment garment);

    Optional<ReferenceGarment> findByChildProfileIdAndPart(Long childProfileId, GarmentPart part);
}
