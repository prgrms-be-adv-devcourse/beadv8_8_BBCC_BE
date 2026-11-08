package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanups;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageCleanup.Reason;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 리뷰 수정 · 삭제 트랜잭션 안에서 정리 대기를 넣고(빠진 사진) 지운다(다시 붙은 사진). 부르는 쪽 트랜잭션에 합류하므로 리뷰 변경이
 * 되돌려지면 대기도 남지 않는다. 이 환경 리뷰 사진의 정리할 수 있는 key로 바뀌는 주소만 다룬다(아니면 넣지 않는다).
 * 대기는 넣은 뒤 pending-delay가 지나야 처리 차례가 된다.
 */
@Component
@RequiredArgsConstructor
public class ReviewImageCleanupQueue {

    private final ReviewImageCleanups cleanups;
    private final ReviewImageCleanupStorage storage;
    private final ReviewImageCleanupProperties properties;

    /**
     * 리뷰에서 빠진 사진을 정리 대기에 넣는다(같은 key는 한 행).
     */
    public void enqueue(Collection<String> imageUrls, Reason reason) {
        List<String> keys = keysOf(imageUrls);
        if (keys.isEmpty()) {
            return;
        }
        LocalDateTime now = ReviewTimes.now();
        LocalDateTime due = now.plus(properties.pendingDelay());
        keys.forEach(key -> cleanups.enqueue(key, reason.name(), now, due));
    }

    /**
     * 리뷰에 다시 붙는 사진의 정리 대기를 지운다.
     */
    public void cancel(Collection<String> imageUrls) {
        List<String> keys = keysOf(imageUrls);
        if (!keys.isEmpty()) {
            cleanups.cancel(keys);
        }
    }

    private List<String> keysOf(Collection<String> imageUrls) {
        return imageUrls.stream().map(storage::keyOf).flatMap(Optional::stream).distinct().toList();
    }
}
