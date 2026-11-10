package com.bbcc.kidly.boundedcontext.recommend.app.member;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
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

    @Transactional
    public void deleteDataOfWithdrawnMember(MemberWithdrawnEvent event) {
        activityRecordService.deleteActivityRecordsOfWithdrawnMember(event);
    }
}
