package com.bbcc.kidly.boundedcontext.review.app.port;

import java.time.LocalDateTime;

/**
 * 자녀 계정 삭제 때 그 계정의 찜을 정리한다(찜 삭제 · 찜 수 차감 · 삭제된 계정 기록은 찜 쪽 일). 구현은 out 어댑터.
 */
public interface ChildFavorites {

    /**
     * @return 지운 찜 수
     */
    int removeAll(Long accountId, Long guardianId, LocalDateTime deletedAt);
}
