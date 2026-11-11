package com.bbcc.kidly.boundedcontext.recommend.out.growth;

import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRecommendGrowthRecordRepository
    extends JpaRepository<RecommendGrowthRecord, Long>, RecommendGrowthRecordRepository {

    // 자녀 삭제 이벤트와 회원 탈퇴 이벤트가 같은 기록을 동시에 지울 수 있다.
    // 파생 delete는 기록을 읽은 뒤 지우므로 그사이 다른 트랜잭션이 먼저 지우면 커밋 때 낙관적 락 예외가 난다.
    // 한 문장 DELETE는 이미 지워졌어도 0행을 지울 뿐 예외가 없고, 이 처리는 기록 엔티티를 읽지 않으므로 영속성 컨텍스트와 어긋나지 않는다
    @Override
    @Modifying
    @Query("delete from RecommendGrowthRecord r where r.childProfileId = :childProfileId")
    void deleteByChildProfileId(@Param("childProfileId") Long childProfileId);
}
