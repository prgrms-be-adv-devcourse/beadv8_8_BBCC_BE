package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 문구에 쓸 상품 이름 복사본(상품당 1행). product.changed의 지금 값을 덮어쓴다.
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

    @Column(nullable = false)
    private LocalDateTime sourceOccurredAt;

    public static ProductName create(Long productId, String name, LocalDateTime occurredAt) {
        var productName = new ProductName();
        productName.productId = productId;
        productName.apply(name, occurredAt);
        return productName;
    }

    /**
     * 저장된 값보다 늦게 발생한 이벤트일 때만 덮어쓴다. 같은 시각은 같은 메시지, 이른 시각은 늦게 온 옛 메시지라 무시한다.
     */
    public void applyIfNewer(String name, LocalDateTime occurredAt) {
        if (truncate(occurredAt).isAfter(sourceOccurredAt)) {
            apply(name, occurredAt);
        }
    }

    private void apply(String name, LocalDateTime occurredAt) {
        this.name = name;
        this.sourceOccurredAt = truncate(occurredAt);
    }

    private static LocalDateTime truncate(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.MICROS);
    }
}
