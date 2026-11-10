package com.bbcc.kidly.boundedcontext.recommend.out.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 구분 하나와 자녀 하나의 삭제는 UNIQUE 때문에 지울 줄이 몇 줄 안 되므로 파생 delete로 둔다.
    // 벌크 DELETE는 영속성 컨텍스트를 거치지 않아 같은 트랜잭션에서 읽고 바꾼 엔티티와 어긋날 수 있다.
    // 회원 단위는 자녀가 여럿이라 한 문장으로 지운다. 자녀 복사본에는 삭제 표시된 자녀도 남아 있어
    // 삭제된 자녀의 기준 옷까지 회원 기준으로 찾을 수 있다
    @Override
    @Modifying
    @Query("""
        delete from ReferenceGarment g
        where g.childProfileId in (select c.childProfileId from RecommendReplicaChild c where c.memberId = :memberId)
        """)
    void deleteAllOfMember(@Param("memberId") Long memberId);
}
