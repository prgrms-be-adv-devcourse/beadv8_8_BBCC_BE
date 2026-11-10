package com.bbcc.kidly.boundedcontext.recommend.app.activity;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ActivityRecordService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

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

    public void recordProductViewed(Long memberId, Long productId, Long childId) {
        if (childId != null) {
            requireVisibleChild(memberId, childId);
        }
        ActivityRecord.ProductViewedInfo info = new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), memberId, childId, productId, LocalDateTime.now(SERVICE_ZONE));
        activityRecordRepository.saveProductViewedIfAbsent(ActivityRecord.productViewed(info));
    }

    private void requireVisibleChild(Long memberId, Long childId) {
        Optional<RecommendReplicaChild> child = replicaChildRepository.findById(childId);
        if (child.isEmpty() || !child.get().isVisibleTo(memberId)) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }
    }

    public void deleteActivityRecordsOfWithdrawnMember(MemberWithdrawnEvent event) {
        // null로 조건을 걸면 어떤 기록도 지우지 못하면서 처리한 것처럼 끝나기 때문에 회원 ID가 없으면 예외로 실패시킨다
        Long memberId = requireNonNull(event.data().memberId(), "memberId는 필수입니다");
        activityRecordRepository.deleteByMemberId(memberId);
    }
}
