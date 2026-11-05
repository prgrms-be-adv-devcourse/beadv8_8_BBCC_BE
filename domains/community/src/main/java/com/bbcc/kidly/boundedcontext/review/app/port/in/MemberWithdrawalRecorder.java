package com.bbcc.kidly.boundedcontext.review.app.port.in;

import java.time.LocalDateTime;

/**
 * 회원 탈퇴를 커뮤니티에 반영한다: 찜은 모두 지우고(찜 수 차감), 리뷰는 남기고 작성자를 탈퇴로 표시한다. 한 트랜잭션.
 * 같은 회원의 탈퇴가 다시 와도 1회분이다.
 */
public interface MemberWithdrawalRecorder {

    void record(Long memberId, LocalDateTime withdrawnAt);
}
