package com.bbcc.kidly.boundedcontext.activity.app;

import com.bbcc.kidly.boundedcontext.activity.domain.Activity;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity.DecidedBy;
import com.bbcc.kidly.boundedcontext.activity.domain.ActivityAttribute;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 커뮤니티 흐름 카운터(이름 · 라벨 값은 이 클래스에만, 라벨은 고정 집합). 활동을 넣은 트랜잭션이 커밋된 뒤에만 오른다(롤백이면 그대로).
 * 작성 자격 생성 · 백필은 세지 않는다.
 * <ul>
 *   <li>{@code kidly.community.reviews{action=created|updated|deleted}}</li>
 *   <li>{@code kidly.community.favorites{action=added, cause=none}} ·
 *       {@code {action=removed, cause=user|withdrawn}}</li>
 *   <li>{@code kidly.community.moderation.decisions{by=moderator, status=passed|hidden}} ·
 *       {@code {by=admin, status=confirmed_hidden|restored}}</li>
 * </ul>
 */
@Component
public class ActivityMetrics {

    public static final String REVIEWS = "kidly.community.reviews";
    public static final String FAVORITES = "kidly.community.favorites";
    public static final String MODERATION_DECISIONS = "kidly.community.moderation.decisions";

    private final Map<String, Counter> counters = new HashMap<>();

    public ActivityMetrics(MeterRegistry registry) {
        for (String action : new String[] {"created", "updated", "deleted"}) {
            counters.put(REVIEWS + "|" + action, Counter.builder(REVIEWS).tag("action", action)
                .description("리뷰 작성 · 수정 · 삭제 수(커밋된 것만)").register(registry));
        }
        favoriteCounter(registry, "added", "none");
        favoriteCounter(registry, "removed", "user");
        favoriteCounter(registry, "removed", "withdrawn");
        decisionCounter(registry, DecidedBy.MODERATOR, "passed");
        decisionCounter(registry, DecidedBy.MODERATOR, "hidden");
        decisionCounter(registry, DecidedBy.ADMIN, "confirmed_hidden");
        decisionCounter(registry, DecidedBy.ADMIN, "restored");
    }

    /**
     * 지금 트랜잭션이 커밋되면 그 활동의 카운터를 count만큼 올린다. 카운터가 없는 활동(작성 자격)은 무시한다.
     */
    void countAfterCommit(Activity activity, int count) {
        Counter counter = counterOf(activity);
        if (counter == null || count <= 0) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment(count);
            }
        });
    }

    private Counter counterOf(Activity activity) {
        Map<ActivityAttribute, Object> attributes = activity.attributes();
        return switch (activity.type()) {
            case ELIGIBILITY_CREATED -> null;
            case REVIEW_CREATED -> counters.get(REVIEWS + "|created");
            case REVIEW_UPDATED -> counters.get(REVIEWS + "|updated");
            case REVIEW_DELETED -> counters.get(REVIEWS + "|deleted");
            case FAVORITE_ADDED -> counters.get(FAVORITES + "|added|none");
            case FAVORITE_REMOVED -> counters.get(FAVORITES + "|removed|" + attributes.get(ActivityAttribute.CAUSE));
            case MODERATION_DECIDED -> counters.get(MODERATION_DECISIONS + "|" + attributes.get(ActivityAttribute.BY)
                + "|" + String.valueOf(attributes.get(ActivityAttribute.STATUS)).toLowerCase(Locale.ROOT));
        };
    }

    private void favoriteCounter(MeterRegistry registry, String action, String cause) {
        counters.put(FAVORITES + "|" + action + "|" + cause, Counter.builder(FAVORITES).tag("action", action)
            .tag("cause", cause).description("찜 추가 · 삭제 수(커밋된 것만)").register(registry));
    }

    private void decisionCounter(MeterRegistry registry, DecidedBy by, String status) {
        counters.put(MODERATION_DECISIONS + "|" + by.value() + "|" + status, Counter.builder(MODERATION_DECISIONS)
            .tag("by", by.value()).tag("status", status)
            .description("검사 결과 · 관리자 조치로 검사 상태가 바뀐 수(커밋된 것만)").register(registry));
    }
}
