package com.bbcc.kidly.boundedcontext.recommend.domain.purchase;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매확정 이벤트를 받아 주문상품 하나마다 남기는 구매 기록이다.
 * 다른 도메인의 ID는 FK 없이 값으로만 참조한다.
 * 이벤트를 다시 받을 수 없으므로, 첫 버전에서 읽지 않는 주문 ID · 회원 ID · 옵션 ID도 함께 저장한다.
 */
@Entity
@Table(schema = "recommend", name = "purchase_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 같은 주문상품은 한 번만 기록한다
    @Column(name = "order_item_id", nullable = false, unique = true)
    private Long orderItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "child_profile_id", nullable = false)
    private Long childProfileId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    public record RecordInfo(Long orderItemId, Long orderId, Long memberId, Long childProfileId, Long productId,
        Long productOptionId, String size, LocalDateTime confirmedAt) {
    }

    /**
     * 구매 기록으로 남길 구매확정인지 판단한다. 자녀를 고르지 않았거나 사이즈가 없는 상품의 구매확정은 사이즈 추천에 쓸 수 없어 남기지 않는다.
     */
    public static boolean isRecordable(RecordInfo info) {
        requireNonNull(info, "info는 필수입니다");
        return info.childProfileId() != null && info.size() != null;
    }

    public static PurchaseRecord create(RecordInfo info) {
        requireNonNull(info, "info는 필수입니다");
        PurchaseRecord record = new PurchaseRecord();
        record.orderItemId = requireNonNull(info.orderItemId(), "orderItemId는 필수입니다");
        record.orderId = requireNonNull(info.orderId(), "orderId는 필수입니다");
        record.memberId = requireNonNull(info.memberId(), "memberId는 필수입니다");
        record.childProfileId = requireNonNull(info.childProfileId(), "childProfileId는 필수입니다");
        record.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        record.productOptionId = requireNonNull(info.productOptionId(), "productOptionId는 필수입니다");
        record.size = requireNonNull(info.size(), "size는 필수입니다");
        record.confirmedAt = requireNonNull(info.confirmedAt(), "confirmedAt은 필수입니다");
        return record;
    }
}
