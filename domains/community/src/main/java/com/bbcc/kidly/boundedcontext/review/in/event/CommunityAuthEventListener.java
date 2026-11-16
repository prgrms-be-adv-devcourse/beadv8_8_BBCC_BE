package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.app.port.in.ChildAccountDeletionRecorder;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * child-account.changed를 받아 자녀 계정 삭제(deleted = true)를 커뮤니티에 반영한다(그 계정의 찜 정리). 만들어짐(deleted = false)은
 * 커뮤니티가 쓰지 않아 건너뛴다. 받는 곳: Kafka 리스너({@link CommunityAuthEventKafkaListener}),
 * 스위치가 꺼졌을 때 Spring 수신({@link CommunitySpringEventReceiver}).
 */
@Component
@RequiredArgsConstructor
public class CommunityAuthEventListener {

    private final ChildAccountDeletionRecorder childAccountDeletionRecorder;

    /**
     * 처리할 수 없는 값(데이터 · 계정 ID · 보호자 ID · 발생 시각)이 없으면 WARN 한 줄을 남기고 건너뛴다. 삭제 시각은 이벤트 발생 시각이다.
     *
     * @return 처리했으면 true. 만들어짐이거나 필수값이 없어 건너뛰었으면 false(DLT 재처리에서는 성공으로 보지 않는다)
     */
    public boolean onChildAccountChanged(ChildAccountChangedEvent event) {
        if (!CommunityEventGuard.accept("자녀 계정", event, "accountId", accountIdOf(event),
            missingRequiredValue(event))) {
            return false;
        }
        if (!event.data().deleted()) {
            return false;
        }
        childAccountDeletionRecorder.record(event.data().accountId(), event.data().guardianId(), event.occurredAt());
        return true;
    }

    private static String missingRequiredValue(ChildAccountChangedEvent event) {
        if (event.data() == null) {
            return "missing-data";
        }
        if (event.data().accountId() == null) {
            return "missing-account-id";
        }
        if (event.data().guardianId() == null) {
            return "missing-guardian-id";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        return null;
    }

    private static Long accountIdOf(ChildAccountChangedEvent event) {
        return event.data() == null ? null : event.data().accountId();
    }
}
