package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishTimelineView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishApprovers;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishApprovers.Principal;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishEvents;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishPurchaseRepository;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.PrincipalType;
import com.bbcc.kidly.boundedcontext.wish.domain.WishPurchase;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.CancelReason;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.RejectReason;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequestLine;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.WishCancelledEvent;
import com.bbcc.kidly.shared.order.event.WishRejectedEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매 요청(조르기) 보내기 · 보기 · 거절 · 철회(#999). 승인 = 결제라 승인 API는 없다: 승인자는 예약된 칸으로 주문하기를 부르고
 * (장바구니 주문 확인, #998), 결제가 끝나면 장바구니 구매 이벤트로 결제 완료가 된다({@link WishPurchaseService}).
 * 조르기는 주문을 모르고 장바구니 칸 예약 입구({@link WishCart})만 부른다.
 * <ul>
 *   <li>보내기: 로그인한 자녀가 자기 장바구니 칸을 골라 보낸다. 진행 중인 조르기에 든 칸은 넣을 수 없다(409, BEG-02).
 *       칸을 예약하고(결제 허용 = 승인자들, 예약 ID = 조르기 ID) wish.sent를 낸다</li>
 *   <li>볼 수 있는 사람: 보낸 사람 + 승인자({@link WishApprovers}). 같은 가족의 다른 사람(형제 자녀)은 403, 그 밖은 없는 조르기(404)</li>
 *   <li>거절(승인자) · 철회(보낸 사람, #968): 처리 전에만(@Version 조건 전이 — 동시에 오면 먼저 저장한 쪽만 남고 진 쪽은 409).
 *       예약을 풀고 wish.rejected · wish.cancelled(원인 DIRECT)를 낸다. 칸은 장바구니에 남는다</li>
 * </ul>
 * 이벤트는 같은 트랜잭션에서 낸다(#702, {@link WishEvents}, 키 = 자녀 프로필 ID). 실패 · 롤백 · 동시 처리에서 진 쪽은 이벤트도 남지 않는다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishRequestService {

    private final WishRequestRepository wishRequestRepository;
    private final WishPurchaseRepository wishPurchaseRepository;
    private final WishCatalog wishCatalog;
    private final WishApprovers wishApprovers;
    private final WishCart wishCart;
    private final WishPolicy wishPolicy;
    private final WishEvents wishEvents;

    /**
     * 확인 순서: 자녀 로그인(403) → 칸 고르기(비었거나 겹침 400) → 자녀 프로필 · 승인자(404) → 진행 중 칸(409) → 메시지(400)
     * → 칸 예약(남의 칸 404 · 예약된 칸 409, 장바구니 오류).
     */
    public WishRequestView send(WishActor actor, List<Long> cartItemIds, String message) {
        if (!wishPolicy.canSend(actor)) {
            throw new BusinessException(WishErrorCode.WISH_REQUEST_SEND_FORBIDDEN);
        }
        if (cartItemIds == null || cartItemIds.isEmpty() || cartItemIds.stream().anyMatch(Objects::isNull)
            || new HashSet<>(cartItemIds).size() != cartItemIds.size()) {
            throw new BusinessException(WishErrorCode.INVALID_WISH_REQUEST);
        }
        Principal requester = new Principal(actor.type(), actor.id());
        WishCatalog.Child child = wishCatalog.findChild(actor.id())
            .filter(found -> !found.deleted() && found.memberId().equals(actor.familyId()))
            .orElseThrow(() -> new BusinessException(WishErrorCode.CHILD_NOT_FOUND));
        List<Principal> approvers = wishApprovers.approversOf(requester);
        if (approvers.isEmpty()) {
            throw new BusinessException(WishErrorCode.CHILD_NOT_FOUND);
        }
        if (wishRequestRepository.existsByStatusAndLinesCartItemIdIn(WishRequest.Status.REQUESTED, cartItemIds)) {
            throw new BusinessException(WishErrorCode.WISH_ITEM_IN_REQUEST);
        }
        WishRequest request;
        try {
            request = wishRequestRepository.save(WishRequest.send(actor.type(), actor.id(), child.childId(), message,
                wishPolicy.maxMessageLength()));
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        List<WishCart.ReservedLine> reserved = wishCart.reserveOwned(requester, cartItemIds, approvers,
            request.reservationId());
        Map<Long, Long> productIds = productIdsOf(reserved.stream().map(WishCart.ReservedLine::optionId).toList());
        try {
            request.addLines(reserved.stream()
                .map(line -> new WishRequest.LineValue(line.cartItemId(), productIds.get(line.optionId()),
                    line.optionId(), line.quantity()))
                .toList());
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        wishEvents.publish(new WishSentEvent(UUID.randomUUID(), LocalDateTime.now(), new WishSentEvent.Data(
            request.getId(), child.childId(), child.memberId(), request.getLines().stream()
                .map(line -> new WishSentEvent.Item(line.getProductId(), line.getOptionId(), line.getQuantity()))
                .toList())));
        log.info("조르기 보냄: requestId={} childId={} itemCount={}", request.getId(), child.childId(),
            reserved.size());
        return toViews(List.of(request)).getFirst();
    }

    /**
     * 내가 볼 수 있는 조르기를 최근 것부터: 내가 보낸 것 + 내가 승인자인 것.
     *
     * @param childId 받는 자녀로 거른다. 없으면(null) 모두
     */
    @Transactional(readOnly = true)
    public List<WishRequestView> getRequests(WishActor actor, Long childId) {
        Principal me = new Principal(actor.type(), actor.id());
        Map<PrincipalType, Set<Long>> requesters = new EnumMap<>(PrincipalType.class);
        requesters.computeIfAbsent(me.type(), type -> new HashSet<>()).add(me.id());
        wishApprovers.requestersApprovedBy(me).forEach(requester ->
            requesters.computeIfAbsent(requester.type(), type -> new HashSet<>()).add(requester.id()));
        List<WishRequest> requests = new ArrayList<>();
        requesters.forEach((type, ids) -> requests.addAll(
            wishRequestRepository.findAllByRequestedByTypeAndRequestedByIdInOrderByIdDesc(type, ids)));
        return toViews(requests.stream()
            .filter(request -> childId == null || childId.equals(request.getChildId()))
            .sorted(Comparator.comparing(WishRequest::getId).reversed())
            .toList());
    }

    @Transactional(readOnly = true)
    public WishRequestView getRequest(WishActor actor, Long requestId) {
        return toViews(List.of(findVisible(actor, requestId))).getFirst();
    }

    /**
     * 확인 순서: 조르기(404 · 같은 가족 403) → 승인자(403) → 사유(정책 목록에 없으면 400) → 처리 전(409).
     */
    public WishRequestView reject(WishActor actor, Long requestId, String reason) {
        WishRequest request = findVisible(actor, requestId);
        if (!wishPolicy.canReject(actor, approversOf(request))) {
            throw new BusinessException(WishErrorCode.WISH_REQUEST_DECIDE_FORBIDDEN);
        }
        RejectReason rejectReason = wishPolicy.rejectReasons().stream()
            .filter(candidate -> candidate.name().equals(reason))
            .findFirst()
            .orElseThrow(() -> new BusinessException(WishErrorCode.INVALID_REJECT_REASON));
        requireNoPurchaseInProgress(request);
        try {
            request.reject(rejectReason, actor.type(), actor.id(), LocalDateTime.now());
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        wishRequestRepository.flush();
        wishCart.release(request.reservationId());
        wishEvents.publish(new WishRejectedEvent(UUID.randomUUID(), LocalDateTime.now(), new WishRejectedEvent.Data(
            request.getId(), request.getChildId(), guardianOf(request), request.getLines().stream()
                .map(line -> new WishRejectedEvent.Item(line.getProductId(), line.getOptionId(), line.getQuantity()))
                .toList(),
            rejectReason.name())));
        log.info("조르기 거절: requestId={} reason={}", request.getId(), rejectReason);
        return toViews(List.of(request)).getFirst();
    }

    /**
     * 보낸 사람이 처리 전에 철회한다(#968). 확인 순서: 조르기(404 · 같은 가족 403) → 보낸 사람(403) → 처리 전(409).
     */
    public WishRequestView cancel(WishActor actor, Long requestId) {
        WishRequest request = findVisible(actor, requestId);
        if (!wishPolicy.canCancel(actor, request)) {
            throw new BusinessException(WishErrorCode.WISH_REQUEST_CANCEL_FORBIDDEN);
        }
        requireNoPurchaseInProgress(request);
        try {
            request.cancel(CancelReason.DIRECT, LocalDateTime.now());
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        wishRequestRepository.flush();
        wishCart.release(request.reservationId());
        wishEvents.publish(new WishCancelledEvent(UUID.randomUUID(), LocalDateTime.now(), new WishCancelledEvent.Data(
            request.getId(), request.getChildId(), guardianOf(request), request.getLines().stream()
                .map(line -> new WishCancelledEvent.Item(line.getProductId(), line.getOptionId(), line.getQuantity()))
                .toList(),
            CancelReason.DIRECT.name())));
        log.info("조르기 철회: requestId={} by={}", request.getId(), actor.type());
        return toViews(List.of(request)).getFirst();
    }

    /**
     * 조르기 타임라인(#702 · #999): 조르기 단계(보냄 → 결제 완료 · 거절 · 철회)와 조르기로 산 주문 ID.
     */
    @Transactional(readOnly = true)
    public WishTimelineView getTimeline(WishActor actor, Long requestId) {
        WishRequest request = findVisible(actor, requestId);
        return WishTimelineView.of(request, wishPurchaseRepository.findAllByRequestIdInOrderByIdAsc(
            List.of(request.getId())));
    }

    /**
     * 결제 정리가 커밋됐지만 조르기가 아직 구매 사실을 받지 못한 사이(Kafka 지연)에는 거절 · 철회를 막는다(409): 예약 칸은 주인이 뺄 수
     * 없으므로, 남은 예약 칸이 줄보다 적으면 결제로 빠진 칸이 있다. 칸이 남는 일부 수량 결제는 칸 수로 알 수 없으므로 장바구니가 예약에
     * 남긴 결제 정리 표시(hasPurchase)로도 본다. 처리된 조르기는 여기서 보지 않는다(도메인이 409).
     * 주문한 뒤 결제 전의 거절 · 철회는 막지 않는다(결제되면 칸은 정리되고 조르기는 그 상태로 남는다).
     */
    private void requireNoPurchaseInProgress(WishRequest request) {
        if (request.getStatus() == WishRequest.Status.REQUESTED
            && (wishCart.reservedCartItemIds(request.reservationId()).size() < request.getLines().size()
                || wishCart.hasPurchase(request.reservationId()))) {
            throw new BusinessException(WishErrorCode.WISH_REQUEST_PURCHASE_IN_PROGRESS);
        }
    }

    /**
     * 볼 수 있는 조르기: 보낸 사람 · 승인자. 같은 가족(같은 보호자)의 다른 사람이면 403, 그 밖(없음 포함)은 404.
     */
    private WishRequest findVisible(WishActor actor, Long requestId) {
        WishRequest request = wishRequestRepository.findById(requestId)
            .orElseThrow(() -> new BusinessException(WishErrorCode.WISH_REQUEST_NOT_FOUND));
        if (request.isRequestedBy(actor.type(), actor.id())
            || approversOf(request).contains(new Principal(actor.type(), actor.id()))) {
            return request;
        }
        if (actor.familyId().equals(guardianOf(request))) {
            throw new BusinessException(WishErrorCode.OTHER_CHILD_FORBIDDEN);
        }
        throw new BusinessException(WishErrorCode.WISH_REQUEST_NOT_FOUND);
    }

    private List<Principal> approversOf(WishRequest request) {
        return wishApprovers.approversOf(new Principal(request.getRequestedByType(), request.getRequestedById()));
    }

    /**
     * 받는 자녀의 보호자 회원 ID(이벤트 memberId · 가족 판단). 자녀 복사본이 없으면 null.
     */
    private Long guardianOf(WishRequest request) {
        return wishCatalog.findChild(request.getChildId()).map(WishCatalog.Child::memberId).orElse(null);
    }

    /**
     * 옵션 ID → 상품 ID(주문이 받아 둔 상품 정보). 없는 옵션은 빠진다(줄의 productId는 null).
     */
    private Map<Long, Long> productIdsOf(Collection<Long> optionIds) {
        return wishCatalog.findProductOptions(optionIds).stream()
            .collect(Collectors.toMap(ProductOption::optionId, ProductOption::productId));
    }

    /**
     * 상품 · 판매자 복사본 · 산 것은 한 번씩 읽어 합친다(줄마다 조회하지 않음).
     */
    private List<WishRequestView> toViews(List<WishRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Set<Long> optionIds = requests.stream()
            .flatMap(request -> request.getLines().stream().map(WishRequestLine::getOptionId))
            .collect(Collectors.toSet());
        Map<Long, ProductOption> products = optionIds.isEmpty() ? Map.of()
            : wishCatalog.findProductOptions(optionIds).stream()
                .collect(Collectors.toMap(ProductOption::optionId, Function.identity()));
        Map<Long, String> sellerNames = sellerNamesOf(products.values());
        Map<Long, List<WishPurchase>> purchases = wishPurchaseRepository.findAllByRequestIdInOrderByIdAsc(
                requests.stream().map(WishRequest::getId).toList()).stream()
            .collect(Collectors.groupingBy(WishPurchase::getRequestId));
        return requests.stream()
            .map(request -> toView(request, products, sellerNames, purchases.getOrDefault(request.getId(), List.of())))
            .toList();
    }

    private static WishRequestView toView(WishRequest request, Map<Long, ProductOption> products,
        Map<Long, String> sellerNames, List<WishPurchase> purchases) {
        Set<Long> purchased = purchases.stream().map(WishPurchase::getCartItemId).collect(Collectors.toSet());
        return new WishRequestView(request.getId(), request.getType().name(), request.getChildId(),
            request.getStatus().name(), request.getRequestedByType().name(), request.getRequestedById(),
            request.getMessage(), request.getRequestedAt(), nameOf(request.getPayerType()), request.getPayerId(),
            request.getPaidAt(), nameOf(request.getRejectReason()), request.getRejectedAt(),
            nameOf(request.getCancelReason()), request.getCancelledAt(),
            request.getLines().stream()
                .map(line -> toLine(line, products.get(line.getOptionId()), sellerNames,
                    purchased.contains(line.getCartItemId())))
                .toList(),
            purchases.stream()
                .map(purchase -> new WishRequestView.Purchase(purchase.getOrderId(), purchase.getCartItemId(),
                    purchase.getQuantity(), purchase.getCreatedAt()))
                .toList());
    }

    private Map<Long, String> sellerNamesOf(Collection<ProductOption> products) {
        Set<Long> sellerIds = products.stream().map(ProductOption::sellerId).collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return wishCatalog.findSellerNames(sellerIds);
    }

    private static WishRequestView.Line toLine(WishRequestLine line, ProductOption product,
        Map<Long, String> sellerNames, boolean purchased) {
        if (product == null) {
            return new WishRequestView.Line(line.getCartItemId(), line.getOptionId(), line.getProductId(), null,
                null, null, null, null, line.getQuantity(), null, false, null, purchased);
        }
        return new WishRequestView.Line(line.getCartItemId(), line.getOptionId(), product.productId(),
            product.sellerId(), product.productName(), product.optionName(), product.size(),
            sellerNames.get(product.sellerId()), line.getQuantity(), product.unitPrice(), product.purchasable(),
            product.saleStatus(), purchased);
    }

    private static String nameOf(Enum<?> value) {
        return Objects.isNull(value) ? null : value.name();
    }
}
