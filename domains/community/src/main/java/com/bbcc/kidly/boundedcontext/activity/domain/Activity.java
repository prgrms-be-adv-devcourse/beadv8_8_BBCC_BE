package com.bbcc.kidly.boundedcontext.activity.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 활동 한 건(종류 · 상품 · 대상 · 속성). 종류마다 정해진 속성만 갖도록 만드는 곳은 아래 생성 메서드뿐이다.
 *
 * @param productId 상품 ID(없을 수 있다)
 * @param aggregateId 대상 ID(작성 자격 · 리뷰 · 찜 행)
 * @param attributes 속성(키 순서대로, 값은 null일 수 있다)
 */
public record Activity(ActivityType type, Long productId, long aggregateId, Map<ActivityAttribute, Object> attributes) {

    private static final Set<String> MODERATION_STATUSES = Set.of("PASSED", "HIDDEN", "CONFIRMED_HIDDEN", "RESTORED");

    public Activity {
        Map<ActivityAttribute, Object> copy = new EnumMap<>(ActivityAttribute.class);
        copy.putAll(attributes);
        attributes = Collections.unmodifiableMap(copy);
    }

    public static Activity eligibilityCreated(long eligibilityId, Long productId, long orderItemId) {
        return new Activity(ActivityType.ELIGIBILITY_CREATED, productId, eligibilityId,
            attributes(ActivityAttribute.ORDER_ITEM_ID, orderItemId));
    }

    public static Activity reviewCreated(long reviewId, Long productId, long orderItemId, int rating, String fit,
        int imageCount) {
        return new Activity(ActivityType.REVIEW_CREATED, productId, reviewId,
            attributes(ActivityAttribute.ORDER_ITEM_ID, orderItemId, ActivityAttribute.RATING, rating,
                ActivityAttribute.FIT, fit, ActivityAttribute.IMAGE_COUNT, imageCount));
    }

    /**
     * 수정 뒤 값(별점 · 핏 · 사진 장수)과 본문 · 사진이 바뀌었는지.
     */
    public static Activity reviewUpdated(long reviewId, Long productId, int rating, String fit, int imageCount,
        boolean contentChanged, boolean imagesChanged) {
        return new Activity(ActivityType.REVIEW_UPDATED, productId, reviewId,
            attributes(ActivityAttribute.RATING, rating, ActivityAttribute.FIT, fit, ActivityAttribute.IMAGE_COUNT,
                imageCount, ActivityAttribute.CONTENT_CHANGED, contentChanged, ActivityAttribute.IMAGES_CHANGED,
                imagesChanged));
    }

    public static Activity reviewDeleted(long reviewId, Long productId, int rating, boolean wasPublic) {
        return new Activity(ActivityType.REVIEW_DELETED, productId, reviewId,
            attributes(ActivityAttribute.RATING, rating, ActivityAttribute.WAS_PUBLIC, wasPublic));
    }

    public static Activity favoriteAdded(long favoriteId, Long productId) {
        return new Activity(ActivityType.FAVORITE_ADDED, productId, favoriteId, Map.of());
    }

    public static Activity favoriteRemoved(long favoriteId, Long productId, RemovalCause cause) {
        return new Activity(ActivityType.FAVORITE_REMOVED, productId, favoriteId,
            attributes(ActivityAttribute.CAUSE, cause.value()));
    }

    /**
     * @param status 바뀐 검사 상태(PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED). 그 밖이면 IllegalArgumentException
     * @param reason 걸린 이유 분류(없으면 null)
     */
    public static Activity moderationDecided(long reviewId, Long productId, DecidedBy by, String status,
        String reason) {
        if (!MODERATION_STATUSES.contains(status)) {
            throw new IllegalArgumentException("활동으로 남기지 않는 검사 상태입니다: " + status);
        }
        return new Activity(ActivityType.MODERATION_DECIDED, productId, reviewId,
            attributes(ActivityAttribute.BY, by.value(), ActivityAttribute.STATUS, status, ActivityAttribute.REASON,
                reason));
    }

    /**
     * 속성을 키 이름 → 값으로(저장 · 응답용, 키 순서 유지).
     */
    public Map<String, Object> attributesByKey() {
        Map<String, Object> byKey = new LinkedHashMap<>();
        attributes.forEach((attribute, value) -> byKey.put(attribute.key(), value));
        return byKey;
    }

    private static Map<ActivityAttribute, Object> attributes(Object... pairs) {
        Map<ActivityAttribute, Object> map = new EnumMap<>(ActivityAttribute.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((ActivityAttribute) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /**
     * 찜이 지워진 까닭.
     */
    public enum RemovalCause {
        USER("user"),
        WITHDRAWN("withdrawn");

        private final String value;

        RemovalCause(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * 검사 상태를 바꾼 쪽.
     */
    public enum DecidedBy {
        MODERATOR("moderator"),
        ADMIN("admin");

        private final String value;

        DecidedBy(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
