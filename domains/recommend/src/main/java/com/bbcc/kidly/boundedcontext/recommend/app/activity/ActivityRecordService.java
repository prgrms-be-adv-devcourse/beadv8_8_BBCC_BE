package com.bbcc.kidly.boundedcontext.recommend.app.activity;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ActivityRecordService {

    private final ActivityRecordRepository activityRecordRepository;

    /**
     * 검색 이벤트의 값을 검색 활동 기록으로 저장한다.
     * 같은 이벤트의 기록이 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다. 한 줄만 남는 것은 UNIQUE 제약이 지킨다.
     */
    public void recordSearch(SearchPerformedEvent event) {
        if (activityRecordRepository.existsByEventId(event.eventId())) {
            return;
        }
        SearchPerformedEvent.Data data = event.data();
        ActivityRecord.SearchInfo info = new ActivityRecord.SearchInfo(
            event.eventId(),
            data.memberId(),
            data.keyword(),
            data.categoryId(),
            data.attributeValueIds(),
            data.resultCount(),
            event.occurredAt());
        activityRecordRepository.save(ActivityRecord.searched(info));
    }

    /**
     * 장바구니 담기 이벤트의 값을 담기 활동 기록으로 저장한다.
     * 같은 이벤트의 기록이 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다. 한 줄만 남는 것은 UNIQUE 제약이 지킨다.
     */
    public void recordCartItemAdded(CartItemAddedEvent event) {
        if (activityRecordRepository.existsByEventId(event.eventId())) {
            return;
        }
        // 활동 기록의 칸은 원래 표의 칸 이름(child_profile_id, product_option_id)과 수량 칸 규칙(added_qty, cart_qty)을 따르고
        // 이벤트는 계약 이름(childId, optionId, quantity, cartQuantity)을 쓰므로 여기서 이름을 옮긴다
        CartItemAddedEvent.Data data = event.data();
        ActivityRecord.CartItemAddedInfo info = new ActivityRecord.CartItemAddedInfo(
            event.eventId(),
            data.cartItemId(),
            data.memberId(),
            data.childId(),
            data.productId(),
            data.optionId(),
            data.quantity(),
            data.cartQuantity(),
            event.occurredAt());
        activityRecordRepository.save(ActivityRecord.cartItemAdded(info));
    }

    /**
     * 장바구니 빼기 이벤트의 값을 빼기 활동 기록으로 저장한다. 뺄 때 그 줄의 수량은 장바구니 수량 칸에 둔다.
     * 같은 이벤트의 기록이 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다. 한 줄만 남는 것은 UNIQUE 제약이 지킨다.
     */
    public void recordCartItemRemoved(CartItemRemovedEvent event) {
        if (activityRecordRepository.existsByEventId(event.eventId())) {
            return;
        }
        CartItemRemovedEvent.Data data = event.data();
        ActivityRecord.CartItemRemovedInfo info = new ActivityRecord.CartItemRemovedInfo(
            event.eventId(),
            data.cartItemId(),
            data.memberId(),
            data.childId(),
            data.productId(),
            data.optionId(),
            data.quantity(),
            event.occurredAt());
        activityRecordRepository.save(ActivityRecord.cartItemRemoved(info));
    }
}
