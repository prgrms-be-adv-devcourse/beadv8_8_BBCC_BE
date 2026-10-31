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
 * 상태는 지금 ACTIVE 하나다(승인 · 묶음이 생기면 값 · 칸을 추가만 한다).
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
        ACTIVE
    }

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
        int added = this.quantity + quantity;
        requirePositive(added);
        this.quantity = added;
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
