package com.bbcc.kidly.boundedcontext.delivery.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 배송(판매 주문) 오류. #617 컨텍스트 분리로 주문 OrderErrorCode에서 옮겼다(응답 상태 · title · detail 그대로).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ErrorCode {

    ALREADY_SHIPPED(HttpStatus.CONFLICT, "이미 발송된 주문입니다", "이미 발송된 주문입니다. 판매 주문 목록을 새로고침해 주세요."),
    NOT_SHIPPED(HttpStatus.CONFLICT, "발송 전입니다", "발송 처리를 먼저 해 주세요."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "판매 주문을 찾을 수 없습니다",
        "없는 판매 주문입니다. 판매 주문 목록을 새로고침해 주세요."),
    INVALID_CARRIER(HttpStatus.BAD_REQUEST, "지원하지 않는 택배사입니다",
        "지원하는 택배사는 CJ · EPOST · HANJIN · LOTTE · LOGEN입니다."),
    DELIVERY_CANCELLED(HttpStatus.CONFLICT, "구매자가 취소한 주문입니다", "구매자가 취소한 주문입니다. 발송하지 마세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
