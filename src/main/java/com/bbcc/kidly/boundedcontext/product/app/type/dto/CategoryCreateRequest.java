package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 관리자가 카테고리를 만들 때 보내는 요청이다. JSON의 문자열 ID("123")도 Long으로 받는다.
 *
 * @param parentId 부모 카테고리 ID다. 최상위 카테고리이면 null
 * @param productTypeId 상품 유형 ID다. 말단 카테고리이면 보내고, 중간 카테고리이면 null
 * @param measurementItemIds 말단 카테고리의 필수 실측 항목 ID다. 넣은 순서가 실측 항목 순서가 된다. 없으면 빈 목록을 보낸다
 */
public record CategoryCreateRequest(
    @NotBlank(message = "카테고리 이름을 입력해 주세요.")
    @Size(max = 255, message = "카테고리 이름은 255자 이하로 입력해 주세요.")
    String name,
    Long parentId,
    Long productTypeId,
    @NotNull(message = "실측 항목 ID를 보내 주세요. 없으면 빈 목록을 보내 주세요.")
    List<@NotNull(message = "실측 항목 ID를 넣어 주세요.") Long> measurementItemIds) {

    // 중간 카테고리는 실측 항목을 갖지 않는다. 목록이 null이면 위의 @NotNull이 따로 거절한다
    @AssertTrue(message = "실측 항목은 상품 유형을 고른 카테고리에만 넣어 주세요.")
    public boolean isMeasurementItemsOnlyForLeaf() {
        return productTypeId != null || measurementItemIds == null || measurementItemIds.isEmpty();
    }
}
