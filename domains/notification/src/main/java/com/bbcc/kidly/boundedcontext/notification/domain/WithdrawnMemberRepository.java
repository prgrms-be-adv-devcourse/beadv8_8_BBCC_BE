package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;

/**
 * 탈퇴한 회원 표시. 탈퇴 → 표시 한 방향으로만 바뀌고 지우지 않는다.
 */
public interface WithdrawnMemberRepository {

    // 이미 있으면 처음 시각을 그대로 둔다
    void markIfAbsent(Long memberId, LocalDateTime withdrawnAt);

    boolean exists(Long memberId);
}
