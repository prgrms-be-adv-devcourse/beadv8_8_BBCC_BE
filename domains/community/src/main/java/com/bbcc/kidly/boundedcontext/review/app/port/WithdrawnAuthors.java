package com.bbcc.kidly.boundedcontext.review.app.port;

import java.time.LocalDateTime;

/**
 * 탈퇴한 작성자의 리뷰 표시. 구현은 out/persistence(한 문장).
 */
public interface WithdrawnAuthors {

    /**
     * 그 회원의 리뷰 중 아직 표시하지 않은 것만 닉네임을 지우고 탈퇴 시각을 기록한다(이미 표시한 리뷰는 그대로).
     * 리뷰 수정 시각(updated_at)은 바꾸지 않는다(응답 updatedAt은 리뷰 내용을 고친 시각이다).
     *
     * @return 바꾼 리뷰 수
     */
    int markWithdrawn(Long memberId, LocalDateTime withdrawnAt);
}
