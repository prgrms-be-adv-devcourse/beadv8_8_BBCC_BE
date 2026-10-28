package com.bbcc.kidly.boundedcontext.member.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessTokenRevocationRepository {

    // 회원의 기준 시각을 남긴다. 이미 있으면 더 늦은 시각만 남긴다
    void upsert(Long memberId, LocalDateTime revokedAt, LocalDateTime now);

    List<AccessTokenRevocation> findAllRevokedAfter(LocalDateTime since);

    // 지운 행 수를 돌려준다
    int deleteRevokedBefore(LocalDateTime before);
}
