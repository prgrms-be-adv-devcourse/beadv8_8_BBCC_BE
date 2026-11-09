package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishApprovers.Principal;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.RejectReason;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 조르기 정책 한 곳(#640 · #999). 팀이 정책을 확정하면 이 클래스만 바꾼다. 지금 값은 팀 확정 전 임시값이다.
 * <ul>
 *   <li>보내기: 로그인한 자녀만(자기 장바구니 칸)(#999)</li>
 *   <li>조르기 메시지: 100자까지(선택)(#701)</li>
 *   <li>거절: 승인자만(#999). 승인 = 결제라 승인 API는 없다</li>
 *   <li>철회: 보낸 사람만(#968)</li>
 *   <li>거절 사유: 너무 비싸요(TOO_EXPENSIVE) · 이미 있어요(ALREADY_HAVE) · 다음에 사자(NEXT_TIME) · 기타(OTHER)(#701)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishPolicy {

    static final int MAX_MESSAGE_LENGTH = 100;
    static final List<RejectReason> REJECT_REASONS = List.of(
        RejectReason.TOO_EXPENSIVE, RejectReason.ALREADY_HAVE, RejectReason.NEXT_TIME, RejectReason.OTHER);

    /**
     * 이 사람이 조르기를 보낼 수 있는지.
     */
    public boolean canSend(WishActor actor) {
        return actor.type() == PrincipalType.CHILD;
    }

    /**
     * 조르기 메시지 최대 길이(앞뒤 공백을 뺀 글자 수).
     */
    public int maxMessageLength() {
        return MAX_MESSAGE_LENGTH;
    }

    /**
     * 이 사람이 조르기를 거절할 수 있는지: 그 조르기의 승인자.
     */
    public boolean canReject(WishActor actor, List<Principal> approvers) {
        return approvers.contains(new Principal(actor.type(), actor.id()));
    }

    /**
     * 이 사람이 조르기를 철회할 수 있는지(#968): 보낸 사람(종류 · ID)만.
     */
    public boolean canCancel(WishActor actor, WishRequest request) {
        return request.isRequestedBy(actor.type(), actor.id());
    }

    /**
     * 고를 수 있는 거절 사유(화면 표시 순서).
     */
    public List<RejectReason> rejectReasons() {
        return REJECT_REASONS;
    }
}
