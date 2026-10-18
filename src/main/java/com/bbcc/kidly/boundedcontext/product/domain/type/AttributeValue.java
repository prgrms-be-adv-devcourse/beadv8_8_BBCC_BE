package com.bbcc.kidly.boundedcontext.product.domain.type;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 항목에서 고르는 값이다(예: 사이즈 110). 팔레트 값은 색 코드도 가진다.
 * {@code @SQLRestriction}을 붙이지 않는다. 지운 값 · 카테고리를 이미 쓰는 상품이 그대로 읽혀야 한다(TYPE-09).
 */
@Entity
@Table(schema = "product", name = "attribute_value")
// 같은 항목 안의 값 이름 중복은 지우지 않은 행끼리만 막는다. 마이그레이션이 부분 UNIQUE 인덱스를 만든다
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttributeValue extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id")
    private Attribute attribute;

    @Column(nullable = false)
    private String name;

    private String colorCode;

    public static AttributeValue create(Attribute attribute, String name, String colorCode) {
        requireNonNull(attribute, "attribute는 필수입니다");
        requireNonNull(name, "name은 필수입니다");
        if (name.isBlank()) {
            throw new IllegalArgumentException("목록 값 이름은 비어 있을 수 없습니다");
        }
        AttributeValue value = new AttributeValue();
        value.attribute = attribute;
        value.name = name;
        value.colorCode = colorCode;
        return value;
    }
}
