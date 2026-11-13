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
    DELIVERY_CANCELLED(HttpStatus.CONFLICT, "구매자가 취소한 주문입니다", "구매자가 취소한 주문입니다. 발송하지 마세요."),
    // 판매자 발송 전 취소(#655)
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 판매 주문입니다", "이미 취소된 판매 주문입니다. 판매 주문 목록을 새로고침해 주세요."),
    INVALID_CANCEL_REASON(HttpStatus.BAD_REQUEST, "지원하지 않는 취소 사유입니다",
        "취소 사유는 OUT_OF_STOCK · WRONG_PRODUCT_INFO · OTHER입니다."),
    PARTIAL_CANCEL_DISABLED(HttpStatus.CONFLICT, "판매자 취소를 아직 쓸 수 없습니다",
        "판매자 취소 기능이 꺼져 있습니다. 관리자에게 문의해 주세요."),
    // 판매 주문 목록 상태 필터(#1280): 응답 status와 같은 값 하나만
    INVALID_DELIVERY_STATUS(HttpStatus.BAD_REQUEST, "지원하지 않는 판매 주문 상태입니다",
        "status는 READY · SHIPPING · DELIVERED · CANCELLED 중 하나를 대문자 그대로 보내 주세요. 전체는 status를 빼고 보내 주세요."),
    // 구매자 택배 진행 이력(#1320). 판매자 문구(DELIVERY_NOT_FOUND · NOT_SHIPPED)와 따로 구매자에게 맞춘다
    TRACKING_DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다", "없는 배송입니다. 주문 상세를 새로고침해 주세요."),
    TRACKING_INVALID_NUMBER(HttpStatus.CONFLICT, "운송장 번호를 조회할 수 없습니다",
        "택배사가 운송장 번호를 확인하지 못했어요. 판매자에게 운송장 번호를 문의해 주세요."),
    TRACKING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "택배 조회를 할 수 없습니다",
        "지금 택배 조회를 할 수 없어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;
}
