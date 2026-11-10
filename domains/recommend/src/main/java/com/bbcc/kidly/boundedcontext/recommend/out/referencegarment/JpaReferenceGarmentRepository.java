package com.bbcc.kidly.boundedcontext.recommend.out.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReferenceGarmentRepository
    extends JpaRepository<ReferenceGarment, Long>, ReferenceGarmentRepository {

    // 구분 이름을 문자열로 정렬하면 BOTTOM이 TOP보다 앞서므로, 상의가 먼저 나오도록 순서를 직접 정한다
    @Override
    @Query("""
        select g from ReferenceGarment g
        where g.childProfileId = :childProfileId
        order by case g.part when com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart.TOP
            then 0 else 1 end
        """)
    List<ReferenceGarment> findAllByChildProfileId(@Param("childProfileId") Long childProfileId);
}
