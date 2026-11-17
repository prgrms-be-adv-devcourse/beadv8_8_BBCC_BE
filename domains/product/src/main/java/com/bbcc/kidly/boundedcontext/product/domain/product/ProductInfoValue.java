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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_value_id")
    private AttributeValue attributeValue;

    @Column(columnDefinition = "TEXT")
    private String textValue;

    static ProductInfoValue create(Product product, Attribute attribute, AttributeValue attributeValue) {
        var infoValue = new ProductInfoValue();
        infoValue.product = product;
        infoValue.attribute = attribute;
        infoValue.attributeValue = attributeValue;
        return infoValue;
    }

    static ProductInfoValue createText(Product product, Attribute attribute, String textValue) {
        var infoValue = new ProductInfoValue();
        infoValue.product = product;
        infoValue.attribute = attribute;
        infoValue.textValue = textValue;
        return infoValue;
    }

    boolean sameAs(ProductInfoValue other) {
        return attribute == other.attribute
            && attributeValue == other.attributeValue
            && Objects.equals(textValue, other.textValue);
    }
}
