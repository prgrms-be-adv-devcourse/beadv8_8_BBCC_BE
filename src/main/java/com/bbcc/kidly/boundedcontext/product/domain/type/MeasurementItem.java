package com.bbcc.kidly.boundedcontext.product.domain.type;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.global.error.BusinessException;
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
 * 총장 · 어깨너비처럼 상품을 재는 실측 항목이다. 말단 카테고리가 어떤 실측 항목을 필수로 받는지 정할 때 쓴다.
 * 코드는 실측 값의 키로 쓰이므로 바꾸지 않는다.
 */
@Entity
@Table(schema = "product", name = "measurement_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementItem extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit;

    public static MeasurementItem create(String name, String code, String unit) {
        requireNonNull(name, "name은 필수입니다");
        requireNonNull(code, "code는 필수입니다");
        requireNonNull(unit, "unit은 필수입니다");
        if (name.isBlank()) {
            throw new IllegalArgumentException("실측 항목 이름은 비어 있을 수 없습니다");
        }
        if (code.isBlank()) {
            throw new IllegalArgumentException("실측 항목 코드는 비어 있을 수 없습니다");
        }
        if (unit.isBlank()) {
            throw new IllegalArgumentException("실측 단위는 비어 있을 수 없습니다");
        }
        MeasurementItem item = new MeasurementItem();
        item.name = name;
        item.code = code;
        item.unit = unit;
        return item;
    }

    // 지우지 않은 카테고리가 필수로 연결한 실측 항목(inUse)은 지울 수 없다(TYPE-10: 쓰이는 중인 기준 데이터는 지우지 않는다)
    public void delete(LocalDateTime now, boolean inUse) {
        requireNonNull(now, "now는 필수입니다");
        if (inUse) {
            throw new BusinessException(MeasurementItemErrorCode.MEASUREMENT_ITEM_IN_USE);
        }
        delete(now);
    }
}
