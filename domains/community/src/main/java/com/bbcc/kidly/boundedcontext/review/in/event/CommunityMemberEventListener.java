package com.bbcc.kidly.boundedcontext.review.in.event;

import com.bbcc.kidly.boundedcontext.review.app.port.in.MemberWithdrawalRecorder;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * member.withdrawn을 받아 커뮤니티에 회원 탈퇴를 반영한다(찜 정리 · 리뷰 작성자 탈퇴 표시).
 * 받는 곳: Kafka 리스너({@link CommunityMemberEventKafkaListener}),
 * 스위치가 꺼졌을 때 Spring 수신({@link CommunitySpringEventReceiver}).
 */
@Component
@RequiredArgsConstructor
public class CommunityMemberEventListener {

    private final MemberWithdrawalRecorder memberWithdrawalRecorder;

    /**
     * 처리할 수 없는 값(데이터 · 회원 ID · 발생 시각)이 없으면 WARN 한 줄을 남기고 건너뛴다. 탈퇴 시각은 이벤트 발생 시각이다.
     *
     * @return 처리했으면 true, 필수값이 없어 건너뛰었으면 false(DLT 재처리에서는 성공으로 보지 않는다)
     */
    public boolean onMemberWithdrawn(MemberWithdrawnEvent event) {
        if (CommunityEventGuard.accept("회원", event, "memberId", memberIdOf(event), missingRequiredValue(event))) {
            memberWithdrawalRecorder.record(event.data().memberId(), event.occurredAt());
            return true;
        }
        return false;
    }

    private static String missingRequiredValue(MemberWithdrawnEvent event) {
        if (event.data() == null) {
            return "missing-data";
        }
        if (event.data().memberId() == null) {
            return "missing-member-id";
        }
        if (event.occurredAt() == null) {
            return "missing-occurred-at";
        }
        return null;
    }

    private static Long memberIdOf(MemberWithdrawnEvent event) {
        return event.data() == null ? null : event.data().memberId();
    }
}
