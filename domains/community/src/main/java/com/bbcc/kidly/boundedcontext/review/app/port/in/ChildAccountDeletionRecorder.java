package com.bbcc.kidly.boundedcontext.review.app.port.in;

import java.time.LocalDateTime;

/**
 * 자녀 계정 삭제를 커뮤니티에 반영한다: 그 계정의 찜을 모두 지우고(찜 수 차감) 그 뒤 그 계정의 찜 요청을 막는다. 한 트랜잭션.
 * 같은 계정의 삭제가 다시 와도 1회분이다.
 */
public interface ChildAccountDeletionRecorder {

    void record(Long accountId, Long guardianId, LocalDateTime deletedAt);
}
