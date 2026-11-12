package com.bbcc.kidly.boundedcontext.recommend.app.member;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴한 회원의 추천 데이터를 한 트랜잭션에서 지운다. 일부만 지워진 채 남지 않게 하기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class RecommendMemberWithdrawalService {

    private final ActivityRecordService activityRecordService;

    private final ReferenceGarmentRepository referenceGarmentRepository;

    private final RecommendGrowthRecordRepository growthRecordRepository;

    @Transactional
    public void deleteDataOfWithdrawnMember(MemberWithdrawnEvent event) {
        Long memberId = requireNonNull(event.data().memberId(), "memberId는 필수입니다");
        activityRecordService.deleteActivityRecordsOfWithdrawnMember(event);
        referenceGarmentRepository.deleteAllOfMember(memberId);
        growthRecordRepository.deleteAllOfMember(memberId);
    }
}
