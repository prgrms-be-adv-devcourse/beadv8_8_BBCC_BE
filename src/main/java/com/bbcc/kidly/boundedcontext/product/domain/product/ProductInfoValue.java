package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
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
 * 상품이 정보 항목 하나에 입력한 값 한 개다. 목록 항목이면 고른 목록 값을, 직접 입력 항목이면 입력한 글을 담는다.
 */
@Entity
@Table(schema = "product", name = "product_info_value")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInfoValue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id")
    private Attribute attribute;

    // 직접 입력 항목이면 null이다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_value_id")
    private AttributeValue attributeValue;

    // 직접 입력 항목의 값이다. 목록 항목이면 null이다
    @Column(columnDefinition = "TEXT")
    private String textValue;

    static ProductInfoValue create(Product product, Attribute attribute, AttributeValue attributeValue) {
        ProductInfoValue infoValue = new ProductInfoValue();
        infoValue.product = product;
        infoValue.attribute = attribute;
        infoValue.attributeValue = attributeValue;
        return infoValue;
    }

    // 직접 입력 항목의 값 한 행을 만든다
    static ProductInfoValue createText(Product product, Attribute attribute, String textValue) {
        ProductInfoValue infoValue = new ProductInfoValue();
        infoValue.product = product;
        infoValue.attribute = attribute;
        infoValue.textValue = textValue;
        return infoValue;
    }

    // 이 행이 그 항목의 그 목록 값을 담고 있는지 본다. 항목과 목록 값은 같은 영속성 컨텍스트의 객체라 ==로 비교한다
    boolean holds(Attribute attribute, AttributeValue value) {
        return this.attribute == attribute && attributeValue == value;
    }

    // 이 행이 그 항목의 그 직접 입력한 글을 담고 있는지 본다
    boolean holdsText(Attribute attribute, String text) {
        return this.attribute == attribute && attributeValue == null && text.equals(textValue);
    }
}
