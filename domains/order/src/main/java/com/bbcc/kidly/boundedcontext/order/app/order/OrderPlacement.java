package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderResult;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 요청 키(#351)가 있는 주문하기. 일부러 트랜잭션을 걸지 않는다: 같은 키로 동시에 만들면 늦은 쪽이 유니크 제약에 걸리고,
 * PostgreSQL은 그 트랜잭션을 더 쓸 수 없게 하므로 먼저 만든 주문은 OrderService.replay의 새 트랜잭션에서 읽는다.
 */
@Component
@RequiredArgsConstructor
public class OrderPlacement {

    private static final int UUID_LENGTH = 36;

    private final OrderService orderService;

    /**
     * @param rawKey Idempotency-Key 헤더 값. 없으면 null(지금처럼 매번 새 주문). UUID 표준 표기(8-4-4-4-12, 36자)가 아니면 400.
     */
    public PlaceOrderResult place(Long memberId, PlaceOrderCommand command, String rawKey) {
        String idempotencyKey = rawKey == null ? null : normalize(rawKey);
        String requestHash = idempotencyKey == null ? null : OrderRequestFingerprint.of(command);
        try {
            return orderService.placeIdempotently(memberId, command, idempotencyKey, requestHash);
        } catch (DataIntegrityViolationException e) {
            if (idempotencyKey == null || !violatesRequestKey(e)) {
                throw e;
            }
            return orderService.replay(memberId, idempotencyKey, requestHash).orElseThrow(() -> e);
        }
    }

    // UUID.fromString은 "1-1-1-1-1" 같은 짧은 형식이나 대시 위치가 다른 36자도 다른 표기로 바꿔 받으므로,
    // 길이를 먼저 보고 다시 쓴 표준 표기가 원문(소문자)과 같을 때만 받는다. 대소문자는 소문자 하나로 맞춘다
    private static String normalize(String rawKey) {
        if (rawKey.length() != UUID_LENGTH) {
            throw new BusinessException(OrderErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        String normalized;
        try {
            normalized = UUID.fromString(rawKey).toString();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        if (!normalized.equals(rawKey.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(OrderErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        return normalized;
    }

    // 제약 이름은 DB 오류 메시지로만 알 수 있어 원인 사슬의 메시지에서 찾는다
    private static boolean violatesRequestKey(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(Order.MEMBER_IDEMPOTENCY_KEY_UNIQUE)) {
                return true;
            }
        }
        return false;
    }
}
