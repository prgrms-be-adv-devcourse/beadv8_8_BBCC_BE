package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.RejectReason;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 조르기 정책 한 곳(#640). 팀이 정책을 확정하면 이 클래스만 바꾼다. 지금 값은 팀 확정 전 임시값이다.
 * <ul>
 *   <li>빼기: 부모는 자기 자녀의 모든 줄, 자녀는 자기가 담은 줄만</li>
 *   <li>같은 옵션 다시 담기: 수량을 합친다(장바구니와 같음)</li>
 *   <li>최대 줄 수: 자녀 한 명당 50줄</li>
 *   <li>조르기 메시지: 100자까지(선택)(#701)</li>
 *   <li>승인 · 거절: 보호자만(#701)</li>
 *   <li>자녀(자녀 로그인, #906)는 자기 프로필의 공용 장바구니 · 조르기만: 다른 자녀 것이면 403</li>
 *   <li>거절 사유: 너무 비싸요(TOO_EXPENSIVE) · 이미 있어요(ALREADY_HAVE) · 다음에 사자(NEXT_TIME) · 기타(OTHER)(#701)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishPolicy {

    static final int MAX_LINES = 50;
    static final int MAX_MESSAGE_LENGTH = 100;
    static final List<RejectReason> REJECT_REASONS = List.of(
        RejectReason.TOO_EXPENSIVE, RejectReason.ALREADY_HAVE, RejectReason.NEXT_TIME, RejectReason.OTHER);

    /**
     * 이 사람이 이 줄을 뺄 수 있는지. 줄의 자녀를 쓸 수 있는지는 이미 확인한 뒤에 묻는다.
     */
    public boolean canRemove(WishActor actor, WishItem item) {
        return actor.type() == AddedBy.PARENT || item.isAddedBy(actor.type(), actor.id());
    }

    /**
     * 같은 자녀에 같은 옵션을 다시 담으면 있던 줄에 수량을 더하는지(false면 새 줄).
     */
    public boolean mergesSameOption() {
        return true;
    }

    /**
     * 자녀 한 명의 공용 장바구니에 둘 수 있는 최대 줄 수(1 이상).
     */
    public int maxLines() {
        return MAX_LINES;
    }

    /**
     * 조르기 메시지 최대 길이(앞뒤 공백을 뺀 글자 수).
     */
    public int maxMessageLength() {
        return MAX_MESSAGE_LENGTH;
    }

    /**
     * 이 사람이 조르기를 승인 · 거절할 수 있는지. 조르기의 자녀를 쓸 수 있는지는 이미 확인한 뒤에 묻는다.
     */
    public boolean canDecide(WishActor actor) {
        return actor.type() == AddedBy.PARENT;
    }

    /**
     * 자녀가 자기 프로필이 아닌 자녀의 공용 장바구니 · 조르기를 다루려는지(#906, 403). 부모는 여기서 막지 않는다(남의 자녀는 404).
     */
    public boolean forbidsOtherChild(WishActor actor, Long childId) {
        return actor.type() == AddedBy.CHILD && !actor.id().equals(childId);
    }

    /**
     * 고를 수 있는 거절 사유(화면 표시 순서).
     */
    public List<RejectReason> rejectReasons() {
        return REJECT_REASONS;
    }
}
