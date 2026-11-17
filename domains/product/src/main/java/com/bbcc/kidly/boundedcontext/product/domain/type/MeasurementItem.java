package com.bbcc.kidly.boundedcontext.product.domain.type;

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
        var item = new MeasurementItem();
        item.name = name;
        item.code = code;
        item.unit = unit;
        return item;
    }

    // 지우지 않은 카테고리가 필수로 연결한 실측 항목(inUse)은 지울 수 없다(TYPE-10: 쓰이는 중인 기준 데이터는 지우지 않는다)
    public void delete(LocalDateTime now, boolean inUse) {
        if (inUse) {
            throw new BusinessException(MeasurementItemErrorCode.MEASUREMENT_ITEM_IN_USE);
        }
        delete(now);
    }
}
