package com.bbcc.kidly.boundedcontext.product.domain.type;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 카테고리에서 생기는 업무 오류다.
 * 상품이 있는 카테고리를 기존 상품과 어긋나게 바꾸는 요청과 하위 카테고리가 있는 카테고리를 지우는 요청은 카테고리의 현재 상태와 충돌하므로 409로 알린다.
 * domain.product가 domain.type을 참조하므로, 반대 방향 참조를 만들지 않으려고 ProductErrorCode와 따로 둔다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {

    NOT_LEAF_CATEGORY(HttpStatus.BAD_REQUEST, "상품을 등록할 수 없는 카테고리입니다", "가장 아래 단계의 카테고리를 골라 주세요."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다", "카테고리를 다시 확인해 주세요."),
    PARENT_IS_LEAF(HttpStatus.BAD_REQUEST, "카테고리를 만들 수 없는 위치입니다", "가장 아래 단계가 아닌 카테고리를 부모로 골라 주세요."),
    MIDDLE_CATEGORY_NOT_CHANGEABLE(HttpStatus.BAD_REQUEST, "설정을 바꿀 수 없는 카테고리입니다",
        "상품 유형과 필수 실측 항목은 가장 아래 단계의 카테고리에서만 바꿀 수 있습니다."),
    DUPLICATE_MEASUREMENT_ITEM(HttpStatus.BAD_REQUEST, "카테고리 설정이 잘못되었습니다", "같은 실측 항목은 한 번만 넣어 주세요."),
    CHANGE_BREAKS_PRODUCTS(HttpStatus.CONFLICT, "상품이 있는 카테고리는 바꿀 수 없는 설정입니다",
        "상품이 있는 카테고리는 필수 실측 항목 빼기와 순서 변경만 할 수 있습니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "지울 수 없는 카테고리입니다",
        "하위 카테고리가 있는 카테고리는 지울 수 없습니다. 하위 카테고리를 먼저 지워 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
