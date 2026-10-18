package com.bbcc.kidly.boundedcontext.product.domain.type;

import static java.util.Objects.requireNonNull;

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

/**
 * 색상 · 사이즈 · 주요소재 같은 항목이다. 코드는 검색 키로 쓰이므로 바꾸지 않는다.
 */
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
        requireNonNull(name, "name은 필수입니다");
        requireNonNull(code, "code는 필수입니다");
        requireNonNull(inputType, "inputType은 필수입니다");
        if (name.isBlank()) {
            throw new IllegalArgumentException("항목 이름은 비어 있을 수 없습니다");
        }
        if (code.isBlank()) {
            throw new IllegalArgumentException("항목 코드는 비어 있을 수 없습니다");
        }
        Attribute attribute = new Attribute();
        attribute.name = name;
        attribute.code = code;
        attribute.inputType = inputType;
        return attribute;
    }

    /**
     * 항목을 지운다. 지우지 않은 상품 유형에 연결된 항목은 지울 수 없다(TYPE-10: 쓰이는 중인 기준 데이터는 지우지 않는다).
     * inUse는 서비스가 리포지토리로 읽어 넘긴다.
     */
    public void delete(LocalDateTime now, boolean inUse) {
        requireNonNull(now, "now는 필수입니다");
        if (inUse) {
            throw new BusinessException(AttributeErrorCode.ATTRIBUTE_IN_USE);
        }
        delete(now);
    }
}
