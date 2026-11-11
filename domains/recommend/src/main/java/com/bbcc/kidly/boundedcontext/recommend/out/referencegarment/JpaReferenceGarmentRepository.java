package com.bbcc.kidly.boundedcontext.recommend.out.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReferenceGarmentRepository
    extends JpaRepository<ReferenceGarment, Long>, ReferenceGarmentRepository {

    List<ReferenceGarment> findByChildProfileId(Long childProfileId);

    // 구분 이름을 문자열로 정렬하면 BOTTOM이 TOP보다 앞서므로, enum 선언 순서로 정렬한다
    @Override
    default List<ReferenceGarment> findAllByChildProfileId(Long childProfileId) {
        List<ReferenceGarment> garments = new ArrayList<>(findByChildProfileId(childProfileId));
        garments.sort(Comparator.comparing(ReferenceGarment::getPart));
        return garments;
    }

    // 구분 하나의 삭제(deleteByChildProfileIdAndPart)는 API가 그 줄을 읽고 지우므로 파생 delete로 둔다.
    // 벌크 DELETE는 영속성 컨텍스트를 거치지 않아 같은 트랜잭션에서 읽고 바꾼 엔티티와 어긋날 수 있기 때문이다.
    // 자녀 단위(자녀 삭제 이벤트)와 회원 단위(탈퇴 이벤트)는 같은 줄을 동시에 지울 수 있다.
    // 파생 delete는 줄을 읽은 뒤 id로 지우므로, 그사이 다른 트랜잭션이 먼저 지우면 0행 삭제가 되어
    // 커밋 때 낙관적 락 예외가 난다. 한 문장 DELETE는 이미 지워졌어도 0행을 지울 뿐 예외가 없다.
    // 이 두 처리는 기준 옷 엔티티를 읽지 않으므로 벌크 DELETE여도 영속성 컨텍스트와 어긋나지 않는다
    @Override
    @Modifying
    @Query("delete from ReferenceGarment g where g.childProfileId = :childProfileId")
    void deleteByChildProfileId(@Param("childProfileId") Long childProfileId);

    // 자녀 복사본에는 삭제 표시된 자녀도 남아 있어 삭제된 자녀의 기준 옷까지 회원 기준으로 찾을 수 있다
    @Override
    @Modifying
    @Query("""
        delete from ReferenceGarment g
        where g.childProfileId in (select c.childProfileId from RecommendReplicaChild c where c.memberId = :memberId)
        """)
    void deleteAllOfMember(@Param("memberId") Long memberId);
}
