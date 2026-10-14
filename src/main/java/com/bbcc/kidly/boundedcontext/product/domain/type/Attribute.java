package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 항목. 색상 · 사이즈 · 주요소재처럼 상품 유형이 옵션이나 상품 정보로 쓰는 속성의 정의.
 */
@Entity
@Table(schema = "product", name = "attribute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attribute extends BaseTimeEntity {

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
        Attribute attribute = new Attribute();
        attribute.name = name;
        attribute.code = code;
        attribute.inputType = inputType;
        return attribute;
    }
}
