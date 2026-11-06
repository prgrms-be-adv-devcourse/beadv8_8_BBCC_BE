package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 알림 문구에 쓸 상품 이름 복사본(상품당 1행). product.changed의 지금 값을 덮어쓴다.
 * 판매자에게 관리자 판매 중지 · 옵션 품절을 알리려고 마지막으로 본 판매 상태도 두고, 바뀐 것을 알려 준다.
 * 발생 시각은 DB 정밀도(마이크로초)로 잘라 저장 · 비교한다. 자르지 않으면 같은 메시지를 다시 받았을 때 늦은 이벤트로 보인다.
 */
@Entity
@Table(schema = "notification", name = "product_name")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductName extends BaseTimeEntity {

    @Id
    private Long productId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductStatus status;

    // null이면 아직 모른다(이 칸을 더하기 전에 저장한 행). 그때는 다음 이벤트로 기준만 잡는다
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Long> inStockOptionIds;

    @Column(nullable = false)
    private LocalDateTime sourceOccurredAt;

    // 처음 받은 상품은 이전 상태를 몰라 알릴 변화가 없다
    public static ProductName create(ProductNameInfo info) {
        var productName = new ProductName();
        productName.productId = info.productId();
        productName.apply(info);
        return productName;
    }

    /**
     * 저장된 값보다 늦게 발생한 이벤트일 때만 덮어쓰고, 알릴 변화를 돌려준다.
     * 같은 시각은 같은 메시지, 이른 시각은 늦게 온 옛 메시지라 무시한다(변화 없음).
     */
    public ProductNameChange applyIfNewer(ProductNameInfo info) {
        if (!truncate(info.occurredAt()).isAfter(sourceOccurredAt)) {
            return ProductNameChange.NONE;
        }
        var change = inStockOptionIds == null ? ProductNameChange.NONE : changeTo(info);
        apply(info);
        return change;
    }

    private ProductNameChange changeTo(ProductNameInfo info) {
        var soldOut = new HashSet<>(inStockOptionIds);
        soldOut.retainAll(info.soldOutOptionIds());
        var stopped = status == ProductStatus.ON_SALE && info.status() == ProductStatus.STOPPED
            && info.stoppedByAdmin();
        return new ProductNameChange(stopped, soldOut);
    }

    private void apply(ProductNameInfo info) {
        this.name = info.name();
        this.status = info.status();
        this.inStockOptionIds = List.copyOf(info.inStockOptionIds());
        this.sourceOccurredAt = truncate(info.occurredAt());
    }

    private static LocalDateTime truncate(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.MICROS);
    }
}
