package com.bbcc.kidly.boundedcontext.recommend.out.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReferenceGarmentRepository
    extends JpaRepository<ReferenceGarment, Long>, ReferenceGarmentRepository {
}
