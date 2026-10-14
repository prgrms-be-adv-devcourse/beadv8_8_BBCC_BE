package com.bbcc.kidly.boundedcontext.product.domain.type;

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
 * 항목에서 고를 수 있는 값이다(예: 사이즈 항목의 110, 색상 항목의 네이비). 팔레트 항목의 값은 화면에 보일 색 코드도 함께 가진다.
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
}
