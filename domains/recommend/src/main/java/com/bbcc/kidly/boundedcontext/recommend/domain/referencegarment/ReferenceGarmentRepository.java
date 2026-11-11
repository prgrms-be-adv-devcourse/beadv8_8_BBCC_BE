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

    /**
     * 회원의 자녀들의 기준 옷을 모두 지운다. 이미 삭제 표시된 자녀의 것도 지운다.
     */
    void deleteAllOfMember(Long memberId);
}
