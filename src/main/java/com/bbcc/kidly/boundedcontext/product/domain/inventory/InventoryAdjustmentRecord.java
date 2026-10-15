package com.bbcc.kidly.boundedcontext.product.domain.inventory;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고를 조정한 내역이다. 만든 뒤에는 바꾸지 않는다.
 */
@Entity
@Table(schema = "product", name = "inventory_adjustment_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryAdjustmentRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_option_id")
    private ProductOption productOption;

    @Column(nullable = false)
    private int deltaQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryAdjustmentReason reason;

    // 조정한 판매자 회원 ID다. 다른 도메인의 ID라 값으로만 가리킨다
    @Column(nullable = false)
    private Long adjustedBy;

    public record AdjustInfo(ProductOption option, int deltaQty, InventoryAdjustmentReason reason, Long adjustedBy) {
    }

    // 판매자가 옵션의 재고를 증감량과 사유로 조정한 기록을 만든다
    public static InventoryAdjustmentRecord adjust(AdjustInfo info) {
        requireNonNull(info, "info는 필수입니다");
        // INITIAL 기록은 옵션을 등록할 때 서버만 남긴다. 판매자가 INITIAL로 재고를 더하면 초기 재고와 입고를 구분할 수 없다 (INV-19)
        if (info.reason() == InventoryAdjustmentReason.INITIAL) {
            throw new IllegalArgumentException("재고 조정에 쓸 수 없는 사유입니다: INITIAL");
        }
        return create(info.option(), info.deltaQty(), info.reason(), info.adjustedBy());
    }

    // 재고를 열 때(InventoryOpening)와 판매자가 조정할 때 만든다. 어느 쪽이든 기록 값은 여기서 검사한다
    static InventoryAdjustmentRecord create(
            ProductOption productOption, int deltaQty, InventoryAdjustmentReason reason, Long adjustedBy) {
        requireNonNull(reason, "reason은 필수입니다");
        // 수량이 바뀌지 않는 조정은 기록하지 않는다 (INV-18)
        if (deltaQty == 0) {
            throw new IllegalArgumentException("증감량은 0이 아니어야 합니다");
        }
        // 사유마다 허용하는 증감량 부호는 InventoryAdjustmentReason에 있다 (INV-18)
        if (!reason.allows(deltaQty)) {
            throw new IllegalArgumentException("사유 " + reason + "에 맞지 않는 증감량입니다: " + deltaQty);
        }
        InventoryAdjustmentRecord record = new InventoryAdjustmentRecord();
        record.productOption = requireNonNull(productOption, "productOption은 필수입니다");
        record.deltaQty = deltaQty;
        record.reason = reason;
        record.adjustedBy = requireNonNull(adjustedBy, "adjustedBy는 필수입니다");
        return record;
    }
}
