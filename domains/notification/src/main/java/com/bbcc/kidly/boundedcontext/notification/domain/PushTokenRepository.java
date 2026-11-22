package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PushTokenRepository {

    /**
     * FID를 이 회원의 것으로 등록한다. 이미 있으면 주인, 마지막 등록 시각을 바꾼다(같은 FID는 한 건).
     */
    void register(Long memberId, String fid, LocalDateTime now);

    int deleteAllButNewest(Long memberId, int keep);

    int deleteByMemberIdAndFid(Long memberId, String fid);

    int deleteByMemberId(Long memberId);

    int deleteByFidIn(Collection<String> fids);

    int deleteRegisteredBefore(LocalDateTime cutoff);

    List<String> findFidsOf(Long memberId);
}
