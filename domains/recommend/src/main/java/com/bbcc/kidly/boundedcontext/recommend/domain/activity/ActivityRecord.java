package com.bbcc.kidly.boundedcontext.recommend.domain.activity;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 다른 도메인의 ID는 FK 없이 값으로만 참조한다.
 * 활동 종류마다 쓰는 칸이 달라, 종류에 해당하지 않는 칸은 null이다.
 */
@Entity
@Table(schema = "recommend", name = "activity_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityRecord extends BaseTimeEntity {

    // 보낸 사람이 직접 거둔 조르기 철회의 원인 값이다
    private static final String DIRECT_CANCEL_REASON = "DIRECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 같은 이벤트는 한 번만 기록한다. 조르기 기록은 원래 이벤트 ID와 조른 상품의 순번으로 만든 ID다
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityType type;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 검색 · 검색 결과 클릭 · 장바구니 담기 · 빼기 · 상세 진입은 자녀를 고른 경우만 값이 있다
    @Column(name = "child_profile_id")
    private Long childProfileId;

    // 활동이 일어난 시각이다. 이벤트로 받은 활동은 이벤트의 발생 시각, 상세 진입은 요청을 받은 시각이다
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(columnDefinition = "TEXT")
    private String keyword;

    @Column(name = "category_id")
    private Long categoryId;

    // 활동을 받은 때의 브랜드다. 선호 집계에서 같은 브랜드를 빼려고 남긴다
    @Column(columnDefinition = "TEXT")
    private String brand;

    // 이 활동의 몫을 선호 집계에서 뺀 시각이다. null이면 아직 집계에 세고 있다
    @Column(name = "uncounted_at")
    private LocalDateTime uncountedAt;

    // 키는 항목 code, 값은 항목 값 ID 목록이다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_value_ids")
    private Map<String, List<Long>> attributeValueIds;

    @Column(name = "result_count")
    private Long resultCount;

    @Column(name = "cart_item_id")
    private Long cartItemId;

    // 조르기 · 조르기 철회만 값이 있다
    @Column(name = "wish_request_id")
    private Long wishRequestId;

    // 찜 · 찜 해제만 값이 있다
    @Column(name = "favorite_id")
    private Long favoriteId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_option_id")
    private Long productOptionId;

    // 이번에 담은 수량이다
    @Column(name = "added_qty")
    private Integer addedQty;

    // 그 시점 장바구니 줄의 수량이다. 담기는 담은 뒤, 빼기는 뺄 때의 수량이다
    @Column(name = "cart_qty")
    private Integer cartQty;

    // 모든 활동 기록에 있어야 하는 종류 · 이벤트 ID · 회원 ID를 채운다. 회원 ID는 탈퇴하면 그 회원의 기록을 지우는 데 쓴다
    private static ActivityRecord of(ActivityType type, UUID eventId, Long memberId) {
        ActivityRecord record = new ActivityRecord();
        record.type = type;
        record.eventId = requireNonNull(eventId, "eventId는 필수입니다");
        record.memberId = requireNonNull(memberId, "memberId는 필수입니다");
        return record;
    }

    public record SearchInfo(UUID eventId, Long memberId, String keyword, Long categoryId,
        Map<String, List<Long>> attributeValueIds, long resultCount, LocalDateTime occurredAt, Long childProfileId) {
    }

    public static ActivityRecord searched(SearchInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = of(ActivityType.SEARCH, info.eventId(), info.memberId());
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        record.keyword = requireNonNull(info.keyword(), "keyword는 필수입니다");
        record.categoryId = info.categoryId();
        record.attributeValueIds = info.attributeValueIds();
        record.resultCount = info.resultCount();
        record.childProfileId = info.childProfileId();
        return record;
    }

    public record CartItemAddedInfo(UUID eventId, Long cartItemId, Long memberId, Long childProfileId,
        Long productId, Long productOptionId, int addedQty, int cartQty, LocalDateTime occurredAt) {
    }

    public static ActivityRecord cartItemAdded(CartItemAddedInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = of(ActivityType.CART_ITEM_ADDED, info.eventId(), info.memberId());
        record.cartItemId = requireNonNull(info.cartItemId(), "cartItemId는 필수입니다");
        record.childProfileId = info.childProfileId();
        record.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        record.productOptionId = requireNonNull(info.productOptionId(), "productOptionId는 필수입니다");
        record.addedQty = info.addedQty();
        record.cartQty = info.cartQty();
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }

    public record CartItemRemovedInfo(UUID eventId, Long cartItemId, Long memberId, Long childProfileId,
        Long productId, Long productOptionId, int cartQty, LocalDateTime occurredAt) {
    }

    public static ActivityRecord cartItemRemoved(CartItemRemovedInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = of(ActivityType.CART_ITEM_REMOVED, info.eventId(), info.memberId());
        record.cartItemId = requireNonNull(info.cartItemId(), "cartItemId는 필수입니다");
        record.childProfileId = info.childProfileId();
        // 주문이 상품 정보를 받아 두지 않았으면 상품 ID 없이 온다
        record.productId = info.productId();
        record.productOptionId = requireNonNull(info.productOptionId(), "productOptionId는 필수입니다");
        record.cartQty = info.cartQty();
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }

    public record ProductViewedInfo(UUID eventId, Long memberId, Long childProfileId, Long productId,
        LocalDateTime occurredAt) {
    }

    public static ActivityRecord productViewed(ProductViewedInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = of(ActivityType.PRODUCT_VIEWED, info.eventId(), info.memberId());
        record.childProfileId = info.childProfileId();
        record.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }

    public record SearchResultClickedInfo(UUID eventId, Long memberId, Long childProfileId, Long productId,
        LocalDateTime occurredAt) {
    }

    public static ActivityRecord searchResultClicked(SearchResultClickedInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = of(ActivityType.SEARCH_RESULT_CLICKED, info.eventId(), info.memberId());
        record.childProfileId = info.childProfileId();
        record.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }

    /**
     * 조르기 하나의 값이다. 조른 상품은 이벤트에 담긴 순서 그대로이고, 상품 ID가 없는 상품도 빼지 않고 담는다.
     */
    public record WishSentInfo(UUID eventId, Long wishRequestId, Long memberId, Long childProfileId,
        List<WishedItem> items, LocalDateTime occurredAt) {
    }

    /**
     * 조른 상품 하나다. 상품 ID는 주문이 상품 정보를 받아 두지 않았으면 null이다.
     */
    public record WishedItem(Long productId, Long productOptionId) {
    }

    /**
     * 조른 상품마다 조르기 활동 기록 한 줄을 만든다. 상품 ID가 없는 상품은 카테고리 · 브랜드 · 벡터를 알 수 없어 만들지 않는다.
     * 기록마다 event_id가 달라야 하므로 원래 이벤트 ID와 조른 상품의 순번(건너뛴 상품도 센다)으로 이름 기반 UUID를 만든다.
     * 같은 이벤트를 다시 받아도 같은 ID가 나와 이미 남긴 줄을 알아볼 수 있다.
     */
    public static List<ActivityRecord> wishSent(WishSentInfo info) {
        requireNonNull(info, "info는 필수입니다");
        UUID eventId = requireNonNull(info.eventId(), "eventId는 필수입니다");
        List<WishedItem> items = requireNonNull(info.items(), "items는 필수입니다");
        List<ActivityRecord> records = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            WishedItem item = requireNonNull(items.get(i), "item은 필수입니다");
            if (item.productId() == null) {
                continue;
            }
            records.add(wishSentItem(info, itemEventId(eventId, i), item));
        }
        return records;
    }

    // 조르기는 늘 자녀의 활동이라 자녀 ID도 필수다. 수량은 선호 계산에 쓰지 않아 남기지 않는다
    private static ActivityRecord wishSentItem(WishSentInfo info, UUID itemEventId, WishedItem item) {
        ActivityRecord record = of(ActivityType.WISH_SENT, itemEventId, info.memberId());
        record.wishRequestId = requireNonNull(info.wishRequestId(), "wishRequestId는 필수입니다");
        record.childProfileId = requireNonNull(info.childProfileId(), "childProfileId는 필수입니다");
        record.productId = item.productId();
        record.productOptionId = requireNonNull(item.productOptionId(), "productOptionId는 필수입니다");
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }

    private static UUID itemEventId(UUID eventId, int itemIndex) {
        return UUID.nameUUIDFromBytes((eventId + ":" + itemIndex).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 조르기 철회가 보낸 자녀가 직접 거둔 것인지 알려 준다. 원인이 없는 예전 메시지는 직접 철회로 본다.
     * 자녀 · 계정 삭제나 시스템 철회는 아이의 관심이 바뀐 것이 아니어서 조르기 몫을 빼지 않는다.
     * 주문이 새 원인을 더해도 모르는 원인은 직접 철회가 아닌 것으로 본다.
     */
    public static boolean isWithdrawnByChild(String cancelReason) {
        return cancelReason == null || DIRECT_CANCEL_REASON.equals(cancelReason);
    }

    public record WishCancelledInfo(UUID eventId, Long wishRequestId, Long memberId, Long childProfileId,
        LocalDateTime occurredAt) {
    }

    // 철회는 조르기 한 건 전체의 취소라 상품 ID를 남기지 않는다. 선호 집계가 조르기 ID로 같은 조르기의 몫을 뺀다
    public static ActivityRecord wishCancelled(WishCancelledInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = of(ActivityType.WISH_CANCELLED, info.eventId(), info.memberId());
        record.wishRequestId = requireNonNull(info.wishRequestId(), "wishRequestId는 필수입니다");
        record.childProfileId = requireNonNull(info.childProfileId(), "childProfileId는 필수입니다");
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }

    // 찜과 찜 해제가 같이 쓰는 값이다
    public record FavoriteInfo(UUID eventId, Long favoriteId, Long memberId, Long productId,
        LocalDateTime occurredAt) {
    }

    public static ActivityRecord favoriteAdded(FavoriteInfo info) {
        return favorite(ActivityType.FAVORITE_ADDED, info);
    }

    // 선호 집계가 찜 ID로 같은 찜의 몫을 뺀다
    public static ActivityRecord favoriteRemoved(FavoriteInfo info) {
        return favorite(ActivityType.FAVORITE_REMOVED, info);
    }

    // 찜은 회원 단위라 자녀가 없다
    private static ActivityRecord favorite(ActivityType type, FavoriteInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = of(type, info.eventId(), info.memberId());
        record.favoriteId = requireNonNull(info.favoriteId(), "favoriteId는 필수입니다");
        record.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }
}
