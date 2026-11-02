package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.MemberFavorites;
import com.bbcc.kidly.boundedcontext.review.app.port.WithdrawnAuthors;
import com.bbcc.kidly.boundedcontext.review.app.port.in.MemberWithdrawalRecorder;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 반영. 찜 정리(찜 쪽, 같은 트랜잭션에 합류)와 리뷰 작성자 탈퇴 표시를 한 트랜잭션에서 한다.
 * 리뷰는 지우지 않고 리뷰 수 · 별점 집계 · 리뷰 수정 시각도 그대로다. 탈퇴 시각은 마이크로초로 잘라 처음 값만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService implements MemberWithdrawalRecorder {

    private final MemberFavorites memberFavorites;
    private final WithdrawnAuthors withdrawnAuthors;

    @Override
    @Transactional
    public void record(Long memberId, LocalDateTime withdrawnAt) {
        memberFavorites.removeAll(memberId);
        withdrawnAuthors.markWithdrawn(memberId, ReviewTimes.toStoredPrecision(withdrawnAt));
    }
}
