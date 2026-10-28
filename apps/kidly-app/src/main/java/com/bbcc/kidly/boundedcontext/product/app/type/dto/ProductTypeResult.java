package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

/**
 * 만든 상품 유형과 연결된 항목이다.
 *
 * @param attributes 연결 순서대로 담는다
 */
public record ProductTypeResult(Long productTypeId, String name, List<Link> attributes) {

    /**
     * @param inputType 입력 방식의 이름(예: PALETTE)
     * @param role 항목의 역할 이름(OPTION 또는 INFO)
     * @param allowedValues 모든 값 허용이면 빈 목록이고, 아니면 허용 값을 ID 순서로 담는다
     */
    public record Link(
        Long attributeId,
        String code,
        String name,
        String inputType,
        String role,
        boolean required,
        boolean allValuesAllowed,
        boolean sizeRole,
        boolean priceAffecting,
        List<AttributeResult.Value> allowedValues) {
    }
}
