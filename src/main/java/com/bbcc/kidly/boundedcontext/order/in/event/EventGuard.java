package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.shared.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 리스너의 "필수 값 없음 → WARN 한 줄 후 무시" 가드. 이벤트 이름은 문자열 대신 이벤트의 eventType()을 쓴다.
 * 빠진 값을 찾는 판단은 각 리스너가 하고, 여기서는 결과에 따라 한 줄 남기기만 한다.
 */
@Slf4j
final class EventGuard {

    private EventGuard() {
    }

    /**
     * @param subject 로그 앞머리의 대상 이름(예: 재고)
     * @param missing 빠진 값의 reason 코드. 빠진 값이 없으면 null
     * @return 처리해도 되면 true. 빠진 값이 있으면 WARN 한 줄을 남기고 false
     */
    static boolean accept(String subject, DomainEvent event, String missing) {
        if (missing == null) {
            return true;
        }
        log.warn("{} 이벤트 무시: event={} reason={}", subject, event.eventType(), missing);
        return false;
    }

    /**
     * 로그 같은 줄에 대상 ID(이름=값)를 함께 남긴다. ID를 아직 모르면 null로 찍는다.
     */
    static boolean accept(String subject, DomainEvent event, String idName, Object id, String missing) {
        if (missing == null) {
            return true;
        }
        log.warn("{} 이벤트 무시: event={} {}={} reason={}", subject, event.eventType(), idName, id, missing);
        return false;
    }
}
