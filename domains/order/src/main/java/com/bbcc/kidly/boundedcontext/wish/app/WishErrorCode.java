package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 조르기 오류(#640 · #999). 칸 예약 오류(남의 칸 · 이미 예약된 칸)는 장바구니 오류(CartErrorCode)가 그대로 나간다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum WishErrorCode implements ErrorCode {

    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "없는 자녀입니다. 자녀를 다시 선택해 주세요."),
    WISH_ITEM_IN_REQUEST(HttpStatus.CONFLICT, "조르는 중인 상품입니다",
        "진행 중인 조르기에 든 장바구니 상품은 다른 조르기에 넣을 수 없습니다."),
    INVALID_WISH_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 조르기입니다",
        "조를 장바구니 상품을 하나 이상 겹치지 않게 고르고, 메시지는 정해진 길이 안으로 적어 주세요."),
    WISH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "조르기를 찾을 수 없습니다", "없는 조르기입니다. 목록을 새로고침해 주세요."),
    WISH_REQUEST_PURCHASE_IN_PROGRESS(HttpStatus.CONFLICT, "결제가 반영 중인 조르기입니다",
        "보호자가 결제한 상품이 있어 결제가 반영되고 있습니다. 잠시 뒤 목록을 새로고침해 주세요."),
    WISH_REQUEST_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리한 조르기입니다",
        "이미 처리(결제 · 거절 · 철회)한 조르기입니다. 목록을 새로고침해 주세요."),
    OTHER_CHILD_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 자녀의 조르기입니다", "자기 조르기만 볼 수 있습니다."),
    CHILD_ACCOUNT_FORBIDDEN(HttpStatus.FORBIDDEN, "조르기를 쓸 수 없는 자녀 계정입니다",
        "연결된 자녀 정보를 확인할 수 없습니다. 보호자에게 자녀 계정을 확인해 달라고 해 주세요."),
    WISH_REQUEST_SEND_FORBIDDEN(HttpStatus.FORBIDDEN, "조르기를 보낼 수 없습니다", "조르기는 자녀 계정으로만 보낼 수 있습니다."),
    WISH_REQUEST_DECIDE_FORBIDDEN(HttpStatus.FORBIDDEN, "조르기를 거절할 수 없습니다", "조르기는 승인자(보호자)만 거절할 수 있습니다."),
    WISH_REQUEST_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "조르기를 철회할 수 없습니다",
        "조르기는 보낸 사람만 철회할 수 있습니다."),
    INVALID_REJECT_REASON(HttpStatus.BAD_REQUEST, "잘못된 거절 사유입니다", "거절 사유를 목록에서 골라 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
