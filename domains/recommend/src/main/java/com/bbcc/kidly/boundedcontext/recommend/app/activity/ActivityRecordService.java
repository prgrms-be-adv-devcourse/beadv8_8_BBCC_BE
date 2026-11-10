package com.bbcc.kidly.boundedcontext.recommend.app.activity;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ActivityRecordService {

    private final ActivityRecordRepository activityRecordRepository;
    private final RecommendReplicaChildRepository replicaChildRepository;

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
            event.occurredAt(),
            verifiedChildId(data));
        activityRecordRepository.save(ActivityRecord.searched(info));
    }

    // 검색 서비스는 자녀가 그 회원의 것인지 확인하지 못한다. 모르는 자녀 · 다른 회원의 자녀 · 지운 자녀는 기록을 실패시키지 않고 자녀 없이 남긴다
    private Long verifiedChildId(SearchPerformedEvent.Data data) {
        if (data.childId() == null) {
            return null;
        }
        return replicaChildRepository.findById(data.childId())
            .filter(child -> child.isVisibleTo(data.memberId()))
            .map(child -> data.childId())
            .orElse(null);
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

    // 이벤트로 받은 기록이 아니므로 이벤트 ID는 서버가 새로 만든다. 같은 상품을 다시 보면 새 기록이 한 줄 더 남는다
    public void recordProductViewed(Long memberId, Long productId) {
        ActivityRecord.ProductViewedInfo info = new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), memberId, productId, LocalDateTime.now());
        activityRecordRepository.save(ActivityRecord.productViewed(info));
    }

    public void deleteActivityRecordsOfWithdrawnMember(MemberWithdrawnEvent event) {
        // null로 조건을 걸면 어떤 기록도 지우지 못하면서 처리한 것처럼 끝나기 때문에 회원 ID가 없으면 예외로 실패시킨다
        Long memberId = requireNonNull(event.data().memberId(), "memberId는 필수입니다");
        activityRecordRepository.deleteByMemberId(memberId);
    }
}
