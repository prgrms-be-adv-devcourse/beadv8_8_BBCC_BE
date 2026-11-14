package com.bbcc.kidly.boundedcontext.recommend.app.activity;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.FavoriteRemoval;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.community.event.FavoriteAddedEvent;
import com.bbcc.kidly.shared.community.event.FavoriteRemovedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import com.bbcc.kidly.shared.order.event.WishCancelledEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import com.bbcc.kidly.shared.search.event.SearchPerformedEvent;
import com.bbcc.kidly.shared.search.event.SearchResultClickedEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
            event.occurredAt(),
            verifiedChildId(data.memberId(), data.childId()));
        activityRecordRepository.save(ActivityRecord.searched(info));
    }

    // 검색 서비스는 검색 · 클릭의 자녀가 그 회원의 것인지 확인하지 못한다. 모르는 자녀 · 다른 회원의 자녀 · 지운 자녀는 기록을 실패시키지 않고 자녀 없이 남긴다
    private Long verifiedChildId(Long memberId, Long childId) {
        if (childId == null) {
            return null;
        }
        return replicaChildRepository.findById(childId)
            .filter(child -> child.isVisibleTo(memberId))
            .map(child -> childId)
            .orElse(null);
    }

    /**
     * 검색 결과 클릭 이벤트의 값을 클릭 활동 기록으로 저장한다.
     * 검색어 · 순위 · 정렬은 선호 계산에 쓰지 않아 남기지 않는다.
     * 같은 회원 · 자녀 · 상품의 클릭은 같은 날 처음 것만 남긴다.
     */
    public void recordSearchResultClicked(SearchResultClickedEvent event) {
        if (activityRecordRepository.existsByEventId(event.eventId())) {
            return;
        }
        SearchResultClickedEvent.Data data = event.data();
        ActivityRecord.SearchResultClickedInfo info = new ActivityRecord.SearchResultClickedInfo(
            event.eventId(), data.memberId(), verifiedChildId(data.memberId(), data.childId()), data.productId(),
            event.occurredAt());
        activityRecordRepository.saveSearchResultClickedIfAbsent(ActivityRecord.searchResultClicked(info));
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

    /**
     * 조르기 이벤트의 값을 조른 상품마다 조르기 활동 기록 한 줄로 저장한다.
     * 같은 이벤트를 다시 받으면 같은 기록 ID가 나오므로 이미 있는 줄은 조용히 건너뛴다.
     */
    public void recordWishSent(WishSentEvent event) {
        WishSentEvent.Data data = event.data();
        List<ActivityRecord.WishedItem> items = data.items().stream()
            .map(item -> new ActivityRecord.WishedItem(item.productId(), item.optionId()))
            .toList();
        ActivityRecord.WishSentInfo info = new ActivityRecord.WishSentInfo(
            event.eventId(), data.wishRequestId(), data.memberId(), data.childId(), items, event.occurredAt());
        for (ActivityRecord record : ActivityRecord.wishSent(info)) {
            if (activityRecordRepository.existsByEventId(record.getEventId())) {
                continue;
            }
            activityRecordRepository.save(record);
        }
    }

    /**
     * 조르기 철회 이벤트의 값을 철회 활동 기록 한 줄로 저장한다.
     * 보낸 자녀가 직접 거둔 철회만 남기고, 같은 이벤트의 기록이 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다.
     */
    public void recordWishCancelled(WishCancelledEvent event) {
        WishCancelledEvent.Data data = event.data();
        if (!ActivityRecord.isWithdrawnByChild(data.cancelReason())) {
            return;
        }
        if (activityRecordRepository.existsByEventId(event.eventId())) {
            return;
        }
        ActivityRecord.WishCancelledInfo info = new ActivityRecord.WishCancelledInfo(
            event.eventId(), data.wishRequestId(), data.memberId(), data.childId(), event.occurredAt());
        activityRecordRepository.save(ActivityRecord.wishCancelled(info));
    }

    /**
     * 찜 이벤트의 값을 회원의 찜 활동 기록으로 저장한다.
     * 같은 이벤트의 기록이 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다.
     */
    public void recordFavoriteAdded(FavoriteAddedEvent event) {
        if (activityRecordRepository.existsByEventId(event.eventId())) {
            return;
        }
        FavoriteAddedEvent.Data data = event.data();
        ActivityRecord.FavoriteInfo info = new ActivityRecord.FavoriteInfo(
            event.eventId(), data.favoriteId(), data.memberId(), data.productId(), event.occurredAt());
        activityRecordRepository.save(ActivityRecord.favoriteAdded(info));
    }

    /**
     * 찜 해제 이벤트의 값을 그 찜의 해제 활동 기록으로 저장한다.
     * 회원 탈퇴로 지운 찜은 탈퇴 처리 뒤에 늦게 온 그 찜 기록까지 지운다. 여러 번 받아도 결과가 같다.
     * 회원이 직접 거둔 해제만 남기고, 같은 이벤트의 기록이 이미 있으면 다시 받은 이벤트로 보고 조용히 건너뛴다.
     */
    public void recordFavoriteRemoved(FavoriteRemovedEvent event) {
        FavoriteRemovedEvent.Data data = event.data();
        String cause = data.cause() == null ? null : data.cause().name();
        if (FavoriteRemoval.isByWithdrawal(cause)) {
            activityRecordRepository.deleteByFavoriteId(requireNonNull(data.favoriteId(), "favoriteId는 필수입니다"));
            return;
        }
        if (!FavoriteRemoval.isWithdrawnByMember(cause)) {
            return;
        }
        if (activityRecordRepository.existsByEventId(event.eventId())) {
            return;
        }
        ActivityRecord.FavoriteInfo info = new ActivityRecord.FavoriteInfo(
            event.eventId(), data.favoriteId(), data.memberId(), data.productId(), event.occurredAt());
        activityRecordRepository.save(ActivityRecord.favoriteRemoved(info));
    }

    public void recordProductViewed(Long memberId, Long productId, Long childId) {
        if (childId != null) {
            requireVisibleChild(memberId, childId);
        }
        ActivityRecord.ProductViewedInfo info = new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), memberId, childId, productId, LocalDateTime.now(SERVICE_ZONE));
        activityRecordRepository.saveProductViewedIfAbsent(ActivityRecord.productViewed(info));
    }

    public void recordProductQuickViewed(Long memberId, Long productId, Long childId) {
        if (childId != null) {
            requireVisibleChild(memberId, childId);
        }
        ActivityRecord.ProductViewedInfo info = new ActivityRecord.ProductViewedInfo(
            UUID.randomUUID(), memberId, childId, productId, LocalDateTime.now(SERVICE_ZONE));
        activityRecordRepository.saveProductQuickViewedIfAbsent(ActivityRecord.productQuickViewed(info));
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
