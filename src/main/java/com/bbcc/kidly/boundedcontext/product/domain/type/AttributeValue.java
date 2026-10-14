package com.bbcc.kidly.boundedcontext.product.domain.type;

import static java.util.Objects.requireNonNull;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 항목에서 고르는 값이다(예: 사이즈 110). 팔레트 값은 색 코드도 가진다.
 */
@Entity
@Table(
    schema = "product",
    name = "attribute_value",
    uniqueConstraints = @UniqueConstraint(columnNames = {"attribute_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttributeValue extends BaseTimeEntity {

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
        AttributeValue value = new AttributeValue();
        value.attribute = requireNonNull(attribute, "attribute는 필수입니다");
        value.name = requireNonNull(name, "name은 필수입니다");
        value.colorCode = colorCode;
        return value;
    }
}
