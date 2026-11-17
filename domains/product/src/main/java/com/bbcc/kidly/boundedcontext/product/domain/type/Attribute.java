package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "product", name = "attribute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attribute extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeInputType inputType;

    public static Attribute create(String name, String code, AttributeInputType inputType) {
        var attribute = new Attribute();
        attribute.name = name;
        attribute.code = code;
        attribute.inputType = inputType;
        return attribute;
    }

    // 지우지 않은 상품 유형에 연결된 항목(inUse)은 지울 수 없다(TYPE-10: 쓰이는 중인 기준 데이터는 지우지 않는다)
    public void delete(LocalDateTime now, boolean inUse) {
        if (inUse) {
            throw new BusinessException(AttributeErrorCode.ATTRIBUTE_IN_USE);
        }
        delete(now);
    }
}
