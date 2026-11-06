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
import java.time.LocalDateTime;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 같은 이벤트는 한 번만 기록한다
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityType type;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 검색은 늘 null이고, 장바구니 담기 · 빼기는 자녀를 고른 경우만 값이 있다
    @Column(name = "child_profile_id")
    private Long childProfileId;

    // 활동이 일어난 시각이다(이벤트의 발생 시각)
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(columnDefinition = "TEXT")
    private String keyword;

    @Column(name = "category_id")
    private Long categoryId;

    // 키는 항목 code, 값은 항목 값 ID 목록이다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_value_ids")
    private Map<String, List<Long>> attributeValueIds;

    @Column(name = "result_count")
    private Long resultCount;

    @Column(name = "cart_item_id")
    private Long cartItemId;

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

    public record SearchInfo(UUID eventId, Long memberId, String keyword, Long categoryId,
        Map<String, List<Long>> attributeValueIds, long resultCount, LocalDateTime occurredAt) {
    }

    public static ActivityRecord searched(SearchInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = new ActivityRecord();
        record.type = ActivityType.SEARCH;
        record.eventId = requireNonNull(info.eventId(), "eventId는 필수입니다");
        record.memberId = requireNonNull(info.memberId(), "memberId는 필수입니다");
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        record.keyword = requireNonNull(info.keyword(), "keyword는 필수입니다");
        record.categoryId = info.categoryId();
        record.attributeValueIds = info.attributeValueIds();
        record.resultCount = info.resultCount();
        return record;
    }

    public record CartItemAddedInfo(UUID eventId, Long cartItemId, Long memberId, Long childProfileId,
        Long productId, Long productOptionId, int addedQty, int cartQty, LocalDateTime occurredAt) {
    }

    public static ActivityRecord cartItemAdded(CartItemAddedInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ActivityRecord record = new ActivityRecord();
        record.type = ActivityType.CART_ITEM_ADDED;
        record.eventId = requireNonNull(info.eventId(), "eventId는 필수입니다");
        record.cartItemId = requireNonNull(info.cartItemId(), "cartItemId는 필수입니다");
        record.memberId = requireNonNull(info.memberId(), "memberId는 필수입니다");
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
        ActivityRecord record = new ActivityRecord();
        record.type = ActivityType.CART_ITEM_REMOVED;
        record.eventId = requireNonNull(info.eventId(), "eventId는 필수입니다");
        record.cartItemId = requireNonNull(info.cartItemId(), "cartItemId는 필수입니다");
        record.memberId = requireNonNull(info.memberId(), "memberId는 필수입니다");
        record.childProfileId = info.childProfileId();
        // 주문이 상품 정보를 받아 두지 않았으면 상품 ID 없이 온다
        record.productId = info.productId();
        record.productOptionId = requireNonNull(info.productOptionId(), "productOptionId는 필수입니다");
        record.cartQty = info.cartQty();
        record.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return record;
    }
}
