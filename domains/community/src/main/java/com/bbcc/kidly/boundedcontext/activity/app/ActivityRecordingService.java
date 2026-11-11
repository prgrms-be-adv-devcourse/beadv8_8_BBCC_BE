package com.bbcc.kidly.boundedcontext.activity.app;

import com.bbcc.kidly.boundedcontext.activity.app.port.Activities;
import com.bbcc.kidly.boundedcontext.activity.app.port.in.ActivityRecorder;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity.DecidedBy;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity.RemovalCause;
import com.bbcc.kidly.boundedcontext.activity.domain.ActivityTimes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 기록. 부르는 쪽 트랜잭션에만 합류하고(MANDATORY: 트랜잭션 밖에서 부르면 실패) 행을 넣은 뒤, 그 트랜잭션이 커밋되면 흐름 카운터를
 * 올린다(롤백이면 행도 카운터도 남지 않는다). 시각은 넣는 지금이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ActivityRecordingService implements ActivityRecorder {

    private final Activities activities;
    private final ActivityMetrics metrics;

    @Override
    public void record(EligibilityCreated activity) {
        save(Activity.eligibilityCreated(activity.eligibilityId(), activity.productId(), activity.orderItemId()));
    }

    @Override
    public void record(ReviewCreated activity) {
        save(Activity.reviewCreated(activity.reviewId(), activity.productId(), activity.orderItemId(),
            activity.rating(), activity.fit(), activity.imageCount()));
    }

    @Override
    public void record(ReviewUpdated activity) {
        save(Activity.reviewUpdated(activity.reviewId(), activity.productId(), activity.rating(), activity.fit(),
            activity.imageCount(), activity.contentChanged(), activity.imagesChanged()));
    }

    @Override
    public void record(ReviewDeleted activity) {
        save(Activity.reviewDeleted(activity.reviewId(), activity.productId(), activity.rating(),
            activity.wasPublic()));
    }

    @Override
    public void record(FavoriteAdded activity) {
        save(Activity.favoriteAdded(activity.favoriteId(), activity.productId()));
    }

    @Override
    public void record(FavoriteRemoved activity) {
        save(Activity.favoriteRemoved(activity.favoriteId(), activity.productId(), RemovalCause.USER));
    }

    @Override
    public void recordWithdrawn(List<FavoriteRemoved> removed) {
        if (removed.isEmpty()) {
            return;
        }
        List<Activity> rows = removed.stream()
            .map(favorite -> Activity.favoriteRemoved(favorite.favoriteId(), favorite.productId(),
                RemovalCause.WITHDRAWN))
            .toList();
        int inserted = activities.insertAll(rows, ActivityTimes.now());
        metrics.countAfterCommit(rows.getFirst(), inserted);
    }

    @Override
    public void record(ModerationDecided activity) {
        save(Activity.moderationDecided(activity.reviewId(), activity.productId(),
            activity.byAdmin() ? DecidedBy.ADMIN : DecidedBy.MODERATOR, activity.status(), activity.reason()));
    }

    private void save(Activity activity) {
        activities.insert(activity, ActivityTimes.now());
        metrics.countAfterCommit(activity, 1);
    }
}
