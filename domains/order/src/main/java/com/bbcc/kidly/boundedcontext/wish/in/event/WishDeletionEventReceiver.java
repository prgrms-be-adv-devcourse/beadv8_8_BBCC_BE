package com.bbcc.kidly.boundedcontext.wish.in.event;

import com.bbcc.kidly.boundedcontext.wish.app.WishDeletionService;
import com.bbcc.kidly.shared.auth.event.ChildAccountChangedEvent;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 자녀 · 자녀 계정 삭제를 받아 진행 중 조르기를 철회한다(#1001). 같은 Kafka 메시지(child.changed · child-account.changed)는 주문의
 * 복사본 리스너가 그룹 kidly.order로 받으므로(같은 그룹에 리스너를 하나 더 두면 파티션이 나뉘어 메시지가 갈린다), 주문 수신이 삭제를
 * 반영한 뒤 받은 이벤트를 앱 안 Spring 이벤트로 다시 알리고 조르기는 그것을 받는다(shared 이벤트 타입만 알고 주문 코드는 모른다).
 * 같은 트랜잭션에서 동기로 처리하므로(@EventListener) 조르기 철회가 실패하면 복사본 반영 · 장바구니 정리와 함께 롤백되고 Kafka가
 * 재시도한다. 삭제가 아닌 이벤트는 건너뛴다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishDeletionEventReceiver {

    private final WishDeletionService wishDeletionService;

    @EventListener
    public void onChildChanged(ChildChangedEvent event) {
        if (event.data() == null || !event.data().deleted() || event.data().childId() == null) {
            return;
        }
        wishDeletionService.cancelForChildDeleted(event.data().childId());
    }

    @EventListener
    public void onChildAccountChanged(ChildAccountChangedEvent event) {
        if (event.data() == null || !event.data().deleted() || event.data().childProfileId() == null) {
            return;
        }
        wishDeletionService.cancelForAccountDeleted(event.data().childProfileId());
    }
}
