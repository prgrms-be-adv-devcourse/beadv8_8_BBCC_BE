package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 조르기 오류(#640). 장바구니와 같은 판단(상품 옵션 · 자기 상품 · 구매 가능 · 자녀 · 수량)은 CartErrorCode의 같은 이름 항목과
 * 응답(상태 · title · detail)이 같다(#617 컨텍스트별 복사).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum WishErrorCode implements ErrorCode {

    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "잘못된 수량입니다", "수량은 1 이상 99 이하여야 합니다."),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "구매할 수 없는 상품입니다",
        "판매가 중지됐거나 품절된 상품이 있습니다. 장바구니에서 빼고 다시 주문해 주세요."),
    OWN_PRODUCT(HttpStatus.FORBIDDEN, "자기 상품은 살 수 없습니다",
        "자기 상품은 담거나 주문할 수 없습니다. 다른 판매자의 상품을 골라 주세요."),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다",
        "없는 상품 옵션입니다. 상품 화면에서 옵션을 다시 골라 주세요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "없는 자녀입니다. 자녀를 다시 선택해 주세요."),
    WISH_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "조르기 상품을 찾을 수 없습니다",
        "이미 조르기에서 빠진 상품입니다. 목록을 새로고침해 주세요."),
    WISH_ITEM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "조르기에 더 담을 수 없습니다",
        "조르기에 담을 수 있는 상품 수를 넘었습니다. 다른 상품을 빼고 다시 담아 주세요."),
    WISH_ITEM_REMOVE_FORBIDDEN(HttpStatus.FORBIDDEN, "이 상품은 뺄 수 없습니다",
        "다른 사람이 담은 상품은 뺄 수 없습니다."),
    WISH_ITEM_IN_REQUEST(HttpStatus.CONFLICT, "조르는 중인 상품입니다",
        "진행 중인 조르기에 든 상품은 다른 조르기에 넣거나 수량을 바꾸거나 뺄 수 없습니다."),
    INVALID_WISH_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 조르기입니다",
        "조를 상품을 하나 이상 겹치지 않게 고르고, 메시지는 정해진 길이 안으로 적어 주세요."),
    WISH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "조르기를 찾을 수 없습니다", "없는 조르기입니다. 목록을 새로고침해 주세요."),
    WISH_REQUEST_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리한 조르기입니다",
        "이미 처리(승인 · 거절 · 철회)한 조르기입니다. 목록을 새로고침해 주세요."),
    OTHER_CHILD_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 자녀의 조르기입니다", "자기 공용 장바구니와 조르기만 쓸 수 있습니다."),
    CHILD_ACCOUNT_FORBIDDEN(HttpStatus.FORBIDDEN, "조르기를 쓸 수 없는 자녀 계정입니다",
        "연결된 자녀 정보를 확인할 수 없습니다. 보호자에게 자녀 계정을 확인해 달라고 해 주세요."),
    WISH_REQUEST_DECIDE_FORBIDDEN(HttpStatus.FORBIDDEN, "조르기를 처리할 수 없습니다", "조르기는 보호자만 승인하거나 거절할 수 있습니다."),
    WISH_REQUEST_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "조르기를 철회할 수 없습니다",
        "조르기는 보낸 사람만 철회할 수 있습니다."),
    INVALID_APPROVAL(HttpStatus.BAD_REQUEST, "잘못된 승인입니다",
        "조르기에 든 상품을 하나 이상 겹치지 않게 골라 주세요. 바로 결제는 상품마다 가격과 배송지가 필요합니다."),
    INVALID_REJECT_REASON(HttpStatus.BAD_REQUEST, "잘못된 거절 사유입니다", "거절 사유를 목록에서 골라 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
