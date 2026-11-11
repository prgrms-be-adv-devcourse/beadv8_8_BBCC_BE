package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;

/**
 * 탈퇴한 회원 표시. 탈퇴 → 표시 한 방향으로만 바뀌고 지우지 않는다.
 */
public interface WithdrawnMemberRepository {

    // 이미 있으면 처음 시각을 그대로 둔다
    void markIfAbsent(Long memberId, LocalDateTime withdrawnAt);

    boolean exists(Long memberId);

    /**
     * 이 회원의 탈퇴 처리 · 가입 처리 · 회원 토픽 DLT 보관을 회원 단위로 줄 세운다. 호출한 트랜잭션이 끝날 때 풀린다.
     * READ COMMITTED에서는 상대가 아직 커밋하지 않은 탈퇴 표시가 보이지 않으므로, 표시를 확인하기 전에 잡는다.
     */
    void lock(Long memberId);
}
