package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 조르기 정책 한 곳(#640). 팀이 정책을 확정하면 이 클래스만 바꾼다. 지금 값은 팀 확정 전 임시값이다.
 * <ul>
 *   <li>빼기: 부모는 자기 자녀의 모든 줄, 자녀는 자기가 담은 줄만</li>
 *   <li>같은 옵션 다시 담기: 수량을 합친다(장바구니와 같음)</li>
 *   <li>최대 줄 수: 자녀 한 명당 50줄</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishPolicy {

    static final int MAX_LINES = 50;

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
}
