package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageCleanup;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;

/**
 * 리뷰 사진 정리 대기 저장소. 구현은 out/persistence(Spring Data가 만든다). 쓰기는 모두 한 문장(조건부)이라 동시에 겹쳐도 한 번씩만 반영된다.
 */
public interface ReviewImageCleanups {

    /**
     * 대기에 넣는다. 같은 key가 이미 있으면 그대로 둔다(부르는 트랜잭션을 깨지 않게 유일 제약 위반을 내지 않는다).
     *
     * @return 새로 넣었으면 1, 이미 있었으면 0
     */
    int enqueue(String objectKey, String reason, LocalDateTime createdAt, LocalDateTime nextAttemptAt);

    /**
     * 다시 붙은 사진의 대기를 지운다.
     */
    int cancel(Collection<String> objectKeys);

    /**
     * 처리할 차례(next_attempt_at ≤ now)인 행을 다음 시각 · id 순으로.
     */
    List<ReviewImageCleanup> findDue(LocalDateTime now, Limit limit);

    /**
     * 넘긴 key 가운데 대기에 있는 것(한 쿼리).
     */
    List<String> findQueuedKeys(Collection<String> objectKeys);

    /**
     * 처리를 끝낸 행을 지운다(읽은 뒤 다른 처리가 바꾸지 않았을 때만).
     */
    int finish(Long id, int attempts);

    /**
     * 실패를 기록한다(읽은 뒤 다른 처리가 바꾸지 않았을 때만): 시도 +1, 다음 시각, 실패 종류.
     */
    int recordFailure(Long id, int attempts, LocalDateTime nextAttemptAt, String failureType);

    long count();

    /**
     * 가장 오래된 대기의 넣은 시각. 비었으면 null.
     */
    LocalDateTime findOldestCreatedAt();
}
