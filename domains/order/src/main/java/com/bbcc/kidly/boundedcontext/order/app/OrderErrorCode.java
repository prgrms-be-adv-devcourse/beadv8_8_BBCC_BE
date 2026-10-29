package com.bbcc.kidly.boundedcontext.order.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 주문 모듈(주문 · 장바구니 · 배송) 오류. detail은 받는 쪽이 다음에 할 일을 알 수 있게 쓴다(행동이 다르면 코드를 나눈다).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    EMPTY_ORDER_ITEMS(HttpStatus.BAD_REQUEST, "주문상품이 없습니다", "주문할 상품을 하나 이상 골라 주세요."),
    DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "같은 주문상품이 두 번 있습니다", "같은 옵션 · 자녀 조합은 한 줄로 주문해 주세요."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "잘못된 수량입니다", "수량은 1 이상 99 이하여야 합니다."),
    PRICE_CHANGED(HttpStatus.CONFLICT, "가격이 바뀌었습니다",
        "상품 가격이 바뀌었습니다. 장바구니에서 바뀐 가격을 확인한 뒤 다시 주문해 주세요."),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "구매할 수 없는 상품입니다",
        "판매가 중지됐거나 품절된 상품이 있습니다. 장바구니에서 빼고 다시 주문해 주세요."),
    OWN_PRODUCT(HttpStatus.FORBIDDEN, "자기 상품은 살 수 없습니다",
        "자기 상품은 담거나 주문할 수 없습니다. 다른 판매자의 상품을 골라 주세요."),
    CHILD_NOT_ORDERABLE(HttpStatus.CONFLICT, "선택할 수 없는 자녀입니다",
        "삭제됐거나 내 자녀가 아닌 자녀입니다. 자녀를 다시 선택해 주세요."),
    NOT_DELIVERED(HttpStatus.CONFLICT, "배송 완료 전입니다", "배송이 완료된 뒤 구매확정할 수 있습니다."),
    ALREADY_SHIPPED(HttpStatus.CONFLICT, "이미 발송된 주문입니다", "이미 발송된 주문입니다. 판매 주문 목록을 새로고침해 주세요."),
    NOT_SHIPPED(HttpStatus.CONFLICT, "발송 전입니다", "발송 처리를 먼저 해 주세요."),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 옵션을 찾을 수 없습니다",
        "없는 상품 옵션입니다. 상품 화면에서 옵션을 다시 골라 주세요."),
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "자녀를 찾을 수 없습니다", "없는 자녀입니다. 자녀를 다시 선택해 주세요."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 상품을 찾을 수 없습니다",
        "이미 장바구니에서 빠진 상품입니다. 장바구니를 새로고침해 주세요."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다", "없는 주문입니다. 주문 목록에서 다시 선택해 주세요."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "판매 주문을 찾을 수 없습니다",
        "없는 판매 주문입니다. 판매 주문 목록을 새로고침해 주세요."),
    INVALID_CARRIER(HttpStatus.BAD_REQUEST, "지원하지 않는 택배사입니다",
        "지원하는 택배사는 CJ · EPOST · HANJIN · LOTTE · LOGEN입니다."),
    ORDER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "주문상품을 찾을 수 없습니다", "없는 주문상품입니다. 주문 상세를 새로고침해 주세요."),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "잘못된 요청 키입니다", "주문서를 다시 열어 주문해 주세요."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "이미 다른 주문에 쓴 요청입니다", "주문서를 다시 열어 주문해 주세요."),
    RECOVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 반영 실패 기록을 찾을 수 없습니다",
        "없는 기록입니다. 결제 반영 실패 목록을 새로고침해 주세요."),
    RECOVERY_NOT_RETRYABLE(HttpStatus.CONFLICT, "재처리할 수 없는 기록입니다",
        "결제 내용이 주문과 달라 재처리할 수 없습니다. 취소로 종료해 환불해 주세요."),
    RECOVERY_CLOSED(HttpStatus.CONFLICT, "이미 종료된 기록입니다",
        "이미 복구됐거나 취소로 종료된 주문입니다. 결제 반영 실패 목록을 새로고침해 주세요."),
    RECOVERY_ORDER_PAID(HttpStatus.CONFLICT, "이미 결제가 반영된 주문입니다",
        "이미 결제가 반영된 주문입니다. 재처리를 눌러 복구를 마무리해 주세요."),
    INVALID_RECOVERY_STATUS(HttpStatus.BAD_REQUEST, "지원하지 않는 기록 상태입니다",
        "상태는 PENDING · RECOVERED · CANCELLED · NOT_RETRYABLE 중 하나로 보내 주세요."),
    EVENT_FAILURE_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트 처리 실패 기록을 찾을 수 없습니다",
        "없는 기록입니다. 이벤트 처리 실패 목록을 새로고침해 주세요."),
    EVENT_FAILURE_CLOSED(HttpStatus.CONFLICT, "이미 종료된 기록입니다",
        "이미 복구됐거나 종료된 기록입니다. 이벤트 처리 실패 목록을 새로고침해 주세요."),
    INVALID_EVENT_FAILURE_STATUS(HttpStatus.BAD_REQUEST, "지원하지 않는 기록 상태입니다",
        "상태는 PENDING · RECOVERED · CANCELLED 중 하나로 보내 주세요."),
    INVALID_STUCK_KIND(HttpStatus.BAD_REQUEST, "지원하지 않는 멈춘 주문 종류입니다",
        "kind는 STOCK_CHECK_STALE · PAYMENT_EXPIRY_MISSED · PAID_WITHOUT_DELIVERY · RECOVERY_PENDING_STALE · "
            + "SHIPPING_DELAYED · AUTO_CONFIRM_MISSED 중 하나를 대문자 그대로 보내 주세요."),
    CANCEL_NOT_ALLOWED_SHIPPED(HttpStatus.CONFLICT, "이미 발송된 상품이 있습니다",
        "판매자가 발송을 시작해 취소할 수 없습니다. 판매자에게 문의해 주세요."),
    DELIVERY_CANCELLED(HttpStatus.CONFLICT, "구매자가 취소한 주문입니다", "구매자가 취소한 주문입니다. 발송하지 마세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
