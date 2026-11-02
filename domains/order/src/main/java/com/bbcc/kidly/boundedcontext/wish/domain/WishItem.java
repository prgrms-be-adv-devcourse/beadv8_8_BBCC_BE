package com.bbcc.kidly.boundedcontext.wish.domain;

import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException.Rule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조르기(공용 장바구니) 한 줄(#640). 같은 자녀의 줄 목록이 그 자녀의 공용 장바구니다. 가격은 저장하지 않는다.
 * 담은 사람(부모 · 자녀)과 담은 시각(생성 시각)을 남긴다. 같은 옵션을 합칠지 · 최대 줄 수 · 누가 뺄 수 있는지는 app의 WishPolicy가 정한다.
 * 상태: ACTIVE(공용 장바구니에 있음) · PURCHASED(조르기로 산 주문이 결제돼 공용 장바구니에서 빠짐, #701).
 * 진행 중인 조르기에 든 줄은 그 조르기 ID(requestId)로 잠긴다: 다른 조르기에 넣거나 수량을 바꾸거나 뺄 수 없다(#701).
 */
@Entity
@Table(schema = "orders", name = "wish_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishItem extends WishTimeEntity {

    /**
     * 담은 사람 종류.
     */
    public enum AddedBy {
        PARENT,
        CHILD
    }

    public enum Status {
        ACTIVE,
        PURCHASED
    }

    /**
     * 한 줄 수량 상한(장바구니와 같은 값).
     */
    public static final int MAX_QUANTITY = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long childId;

    @Column(nullable = false)
    private Long optionId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddedBy addedByType;

    /**
     * 부모면 회원 ID, 자녀면 자녀 프로필 ID.
     */
    @Column(nullable = false)
    private Long addedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /**
     * 이 줄을 잠근 진행 중인 조르기 ID. 잠기지 않았으면 null(#701).
     */
    private Long requestId;

    @Version
    private Long version;

    public static WishItem create(Long childId, Long optionId, int quantity, AddedBy addedByType, Long addedById) {
        requirePositive(quantity);
        WishItem item = new WishItem();
        item.childId = childId;
        item.optionId = optionId;
        item.quantity = quantity;
        item.addedByType = addedByType;
        item.addedById = addedById;
        item.status = Status.ACTIVE;
        return item;
    }

    /**
     * 같은 옵션을 다시 담아 수량을 더한다. 담은 사람 · 담은 시각은 처음 값 그대로다.
     */
    public void addQuantity(int quantity) {
        requireUnlocked();
        int added = this.quantity + quantity;
        requirePositive(added);
        this.quantity = added;
    }

    /**
     * 조르기에 넣어 잠근다. 이미 다른(또는 같은) 조르기에 들었으면 위반이다.
     */
    public void lockFor(Long requestId) {
        requireUnlocked();
        this.requestId = requestId;
    }

    /**
     * 그 조르기의 잠금을 푼다(거절 · 승인에서 빠짐 · 장바구니로 옮김 · 주문 취소). 그 조르기가 잠근 줄이 아니면 그대로 둔다.
     */
    public void unlockFrom(Long requestId) {
        if (requestId.equals(this.requestId)) {
            this.requestId = null;
        }
    }

    /**
     * 그 조르기로 산 주문이 결제돼 공용 장바구니에서 뺀다(수량과 상관없이 줄 통째로, 장바구니 정리와 같음).
     * 그 조르기가 잠근 줄이 아니면 그대로 둔다.
     */
    public void purchaseBy(Long requestId) {
        if (status == Status.ACTIVE && requestId.equals(this.requestId)) {
            this.requestId = null;
            this.status = Status.PURCHASED;
        }
    }

    public boolean isLocked() {
        return requestId != null;
    }

    /**
     * 진행 중인 조르기에 든 줄은 빼거나 수량을 바꿀 수 없다.
     */
    public void requireUnlocked() {
        if (isLocked()) {
            throw new WishRuleViolationException(Rule.WISH_ITEM_IN_REQUEST);
        }
    }

    public boolean isAddedBy(AddedBy type, Long id) {
        return addedByType == type && addedById.equals(id);
    }

    /**
     * 담은 시각(처음 담은 때).
     */
    public LocalDateTime getAddedAt() {
        return getCreatedAt();
    }

    private static void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new WishRuleViolationException(Rule.INVALID_QUANTITY);
        }
    }
}
