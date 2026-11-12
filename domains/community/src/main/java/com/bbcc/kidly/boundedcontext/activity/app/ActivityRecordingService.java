package com.bbcc.kidly.boundedcontext.activity.app;

import com.bbcc.kidly.boundedcontext.activity.app.port.Activities;
import com.bbcc.kidly.boundedcontext.activity.app.port.in.ActivityRecorder;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity.DecidedBy;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity.RemovalCause;
import com.bbcc.kidly.boundedcontext.activity.domain.ActivityTimes;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 기록. 부르는 쪽 트랜잭션에만 합류하고(MANDATORY: 트랜잭션 밖에서 부르면 실패) 활동 행과 보낼 이벤트(아웃박스 행,
 * {@link CommunityEventOutboxWriter})를 같은 트랜잭션에서 넣은 뒤, 커밋되면 흐름 카운터를 올린다(롤백이면 행 · 이벤트 · 카운터 모두
 * 남지 않는다). 시각은 넣는 지금이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ActivityRecordingService implements ActivityRecorder {

    private final Activities activities;
    private final ActivityMetrics metrics;
    private final CommunityEventOutboxWriter outbox;

    @Override
    public void record(EligibilityCreated activity) {
        save(Activity.eligibilityCreated(activity.eligibilityId(), activity.productId(), activity.orderItemId()),
            ActivityTimes.now());
    }

    @Override
    public void record(ReviewCreated activity) {
        LocalDateTime now = ActivityTimes.now();
        save(Activity.reviewCreated(activity.reviewId(), activity.productId(), activity.orderItemId(),
            activity.rating(), activity.fit(), activity.imageCount()), now);
        outbox.reviewChanged(activity.reviewId(), activity.orderItemId(), activity.productId(), activity.rating(),
            activity.fit(), true, now);
    }

    @Override
    public void record(ReviewUpdated activity) {
        LocalDateTime now = ActivityTimes.now();
        save(Activity.reviewUpdated(activity.reviewId(), activity.productId(), activity.rating(), activity.fit(),
            activity.imageCount(), activity.contentChanged(), activity.imagesChanged()), now);
        outbox.reviewChanged(activity.reviewId(), activity.orderItemId(), activity.productId(), activity.rating(),
            activity.fit(), activity.visible(), now);
    }

    @Override
    public void record(ReviewDeleted activity) {
        LocalDateTime now = ActivityTimes.now();
        save(Activity.reviewDeleted(activity.reviewId(), activity.productId(), activity.rating(),
            activity.wasPublic()), now);
        outbox.reviewChanged(activity.reviewId(), activity.orderItemId(), activity.productId(), activity.rating(),
            activity.fit(), false, now);
    }

    @Override
    public void record(FavoriteAdded activity) {
        LocalDateTime now = ActivityTimes.now();
        save(Activity.favoriteAdded(activity.favoriteId(), activity.productId()), now);
        outbox.favoriteAdded(activity.favoriteId(), activity.memberId(), activity.productId(), now);
    }

    @Override
    public void record(FavoriteRemoved activity) {
        LocalDateTime now = ActivityTimes.now();
        save(Activity.favoriteRemoved(activity.favoriteId(), activity.productId(), RemovalCause.USER), now);
        outbox.favoritesRemoved(List.of(activity), false, now);
    }

    @Override
    public void recordWithdrawn(List<FavoriteRemoved> removed) {
        if (removed.isEmpty()) {
            return;
        }
        LocalDateTime now = ActivityTimes.now();
        List<Activity> rows = removed.stream()
            .map(favorite -> Activity.favoriteRemoved(favorite.favoriteId(), favorite.productId(),
                RemovalCause.WITHDRAWN))
            .toList();
        int inserted = activities.insertAll(rows, now);
        metrics.countAfterCommit(rows.getFirst(), inserted);
        outbox.favoritesRemoved(removed, true, now);
    }

    @Override
    public void record(ModerationDecided activity) {
        LocalDateTime now = ActivityTimes.now();
        Activity decided = Activity.moderationDecided(activity.reviewId(), activity.productId(),
            activity.byAdmin() ? DecidedBy.ADMIN : DecidedBy.MODERATOR, activity.status(), activity.reason());
        save(decided, now);
        outbox.reviewChanged(activity.reviewId(), activity.orderItemId(), activity.productId(), activity.rating(),
            activity.fit(), activity.visible(), now);
    }

    private void save(Activity activity, LocalDateTime now) {
        activities.insert(activity, now);
        metrics.countAfterCommit(activity, 1);
    }
}
