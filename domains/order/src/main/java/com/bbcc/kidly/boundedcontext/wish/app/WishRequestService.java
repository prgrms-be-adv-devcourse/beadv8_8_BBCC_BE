package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.dto.ApproveWishCommand;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishTimelineView;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishEvents;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishItemRepository;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishOrders;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishParentCart;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.Checkout;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequest.RejectReason;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRequestLine;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.WishApprovedEvent;
import com.bbcc.kidly.shared.order.event.WishRejectedEvent;
import com.bbcc.kidly.shared.order.event.WishSentEvent;
import java.time.LocalDateTime;
import java.util.Collection;
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
 * 조르기 보내기 · 보기 · 승인 · 거절(#701). 쓸 수 없는 자녀(남의 자녀 · 삭제된 자녀 · 없는 자녀)의 조르기는 없는 것으로 본다(404).
 * <ul>
 *   <li>보내기: 그 자녀의 공용 장바구니 줄 여러 개 + 메시지(선택). 진행 중인 조르기에 든 줄은 넣을 수 없다(409). 줄은 공용 장바구니에 남는다</li>
 *   <li>승인 · 거절: 보호자만({@link WishPolicy#canDecide}), 한 번만(조르기 @Version — 승인 · 거절을 먼저 DB에 써서 동시에 처리하는
 *       쪽이 주문 · 장바구니를 건드리기 전에 버전 충돌로 진다)</li>
 *   <li>바로 결제: 주문하기와 같은 길로 보호자 주문을 만들고(주문상품 자녀 = 조른 자녀) 주문 ID를 기록한다. 결제가 끝나면 승인한 줄이
 *       공용 장바구니에서 빠진다({@link WishOrderSettlement}). 보호자 장바구니로 옮기기: 장바구니 담기와 같은 길로 담는다</li>
 * </ul>
 * 주문 · 장바구니가 거절하면(가격 바뀜 · 판매 중 아님 등) 그 오류가 그대로 나가고 승인도 롤백된다.
 * 보내기 · 승인 · 거절이 성공하면 같은 트랜잭션에서 wish.sent · wish.approved · wish.rejected를 낸다(#702, {@link WishEvents}, 키 = 자녀
 * 프로필 ID). 실패 · 롤백 · 동시 처리에서 진 쪽은 이벤트도 남지 않는다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishRequestService {

    private final WishRequestRepository wishRequestRepository;
    private final WishItemRepository wishItemRepository;
    private final WishCatalog wishCatalog;
    private final WishOrders wishOrders;
    private final WishParentCart wishParentCart;
    private final WishPolicy wishPolicy;
    private final WishOrderSettlement wishOrderSettlement;
    private final WishEvents wishEvents;

    /**
     * 확인 순서: 자녀 → 줄 고르기(비었거나 겹침 400) → 줄(그 자녀의 공용 장바구니에 없으면 404) → 메시지 · 잠금(400 · 409).
     */
    public WishRequestView send(WishActor actor, Long childId, List<Long> wishItemIds, String message) {
        WishCatalog.Child child = requireUsableChild(actor, childId, WishErrorCode.CHILD_NOT_FOUND);
        if (wishItemIds == null || wishItemIds.isEmpty() || wishItemIds.stream().anyMatch(Objects::isNull)
            || new HashSet<>(wishItemIds).size() != wishItemIds.size()) {
            throw new BusinessException(WishErrorCode.INVALID_WISH_REQUEST);
        }
        wishOrderSettlement.settle(childId);
        Map<Long, WishItem> found = wishItemRepository.findAllByIdIn(wishItemIds).stream()
            .filter(item -> item.getChildId().equals(childId) && item.getStatus() == WishItem.Status.ACTIVE)
            .collect(Collectors.toMap(WishItem::getId, Function.identity()));
        if (found.size() != wishItemIds.size()) {
            throw new BusinessException(WishErrorCode.WISH_ITEM_NOT_FOUND);
        }
        List<WishItem> items = wishItemIds.stream().map(found::get).toList();
        WishRequest request;
        try {
            request = wishRequestRepository.save(WishRequest.send(childId, actor.type(), actor.id(), message,
                wishPolicy.maxMessageLength(), items));
            request.lockItems(items);
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        Map<Long, Long> productIds = productIdsOf(request.getLines());
        wishEvents.publish(new WishSentEvent(UUID.randomUUID(), LocalDateTime.now(), new WishSentEvent.Data(
            request.getId(), childId, child.memberId(), request.getLines().stream()
                .map(line -> new WishSentEvent.Item(productIds.get(line.getOptionId()), line.getOptionId(),
                    line.getRequestedQuantity()))
                .toList())));
        log.info("조르기 보냄: requestId={} childId={} itemCount={}", request.getId(), childId, items.size());
        return toViews(List.of(request)).getFirst();
    }

    /**
     * 그 자녀의 조르기를 최근 것부터. 바로 결제한 조르기는 결제 결과를 반영해 본다(저장하지 않음).
     */
    @Transactional(readOnly = true)
    public List<WishRequestView> getRequests(WishActor actor, Long childId) {
        requireUsableChild(actor, childId, WishErrorCode.CHILD_NOT_FOUND);
        wishOrderSettlement.settle(childId);
        return toViews(wishRequestRepository.findAllByChildIdOrderByIdDesc(childId));
    }

    @Transactional(readOnly = true)
    public WishRequestView getRequest(WishActor actor, Long requestId) {
        WishRequest request = findUsable(actor, requestId);
        wishOrderSettlement.settle(request.getChildId());
        return toViews(List.of(request)).getFirst();
    }

    /**
     * 확인 순서: 조르기(404) → 보호자(403) → 구매 방법 · 바로 결제 값(400) → 처리 전(409) · 고른 줄(400).
     */
    public WishRequestView approve(WishActor actor, Long requestId, ApproveWishCommand command) {
        WishRequest request = findUsable(actor, requestId);
        WishCatalog.Child child = childOf(request);
        requireDecider(actor);
        Checkout checkout = checkoutOf(command);
        List<WishRequest.Approval> approvals = command.items().stream()
            .map(item -> new WishRequest.Approval(item.wishItemId(), item.quantity()))
            .toList();
        List<WishRequestLine> approved;
        try {
            approved = request.approve(approvals, checkout, actor.id(), itemsOf(request), LocalDateTime.now());
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        wishRequestRepository.flush();

        if (checkout == Checkout.ORDER) {
            Map<Long, Long> unitPrices = command.items().stream()
                .collect(Collectors.toMap(ApproveWishCommand.Item::wishItemId, ApproveWishCommand.Item::unitPrice));
            ApproveWishCommand.ShippingAddress address = command.shippingAddress();
            Long orderId = wishOrders.place(actor.id(), request.getChildId(),
                approved.stream()
                    .map(line -> new WishOrders.Line(line.getOptionId(), line.getApprovedQuantity(),
                        unitPrices.get(line.getWishItemId())))
                    .toList(),
                new WishOrders.ShippingAddress(address.recipientName(), address.phone(), address.zipCode(),
                    address.address()));
            request.recordOrder(orderId);
        } else {
            approved.forEach(line -> wishParentCart.put(actor.id(), line.getOptionId(), request.getChildId(),
                line.getApprovedQuantity()));
        }
        Map<Long, Long> productIds = productIdsOf(approved);
        wishEvents.publish(new WishApprovedEvent(UUID.randomUUID(), LocalDateTime.now(), new WishApprovedEvent.Data(
            request.getId(), request.getChildId(), child.memberId(), approved.stream()
                .map(line -> new WishApprovedEvent.Item(productIds.get(line.getOptionId()), line.getOptionId(),
                    line.getApprovedQuantity()))
                .toList())));
        log.info("조르기 승인: requestId={} checkout={} approvedCount={} orderId={}",
            request.getId(), checkout, approved.size(), request.getOrderId());
        return toViews(List.of(request)).getFirst();
    }

    /**
     * 확인 순서: 조르기(404) → 보호자(403) → 사유(정책 목록에 없으면 400) → 처리 전(409).
     */
    public WishRequestView reject(WishActor actor, Long requestId, String reason) {
        WishRequest request = findUsable(actor, requestId);
        WishCatalog.Child child = childOf(request);
        requireDecider(actor);
        RejectReason rejectReason = wishPolicy.rejectReasons().stream()
            .filter(candidate -> candidate.name().equals(reason))
            .findFirst()
            .orElseThrow(() -> new BusinessException(WishErrorCode.INVALID_REJECT_REASON));
        try {
            request.reject(rejectReason, actor.id(), itemsOf(request), LocalDateTime.now());
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        Map<Long, Long> productIds = productIdsOf(request.getLines());
        wishEvents.publish(new WishRejectedEvent(UUID.randomUUID(), LocalDateTime.now(), new WishRejectedEvent.Data(
            request.getId(), request.getChildId(), child.memberId(), request.getLines().stream()
                .map(line -> new WishRejectedEvent.Item(productIds.get(line.getOptionId()), line.getOptionId(),
                    line.getRequestedQuantity()))
                .toList(),
            rejectReason.name())));
        log.info("조르기 거절: requestId={} reason={}", request.getId(), rejectReason);
        return toViews(List.of(request)).getFirst();
    }

    /**
     * 조르기 타임라인(#702): 조르기 단계(보냄 → 승인 · 거절)에 바로 결제한 주문의 주문 타임라인을 이어 붙인다. 새 이력 없이
     * 조르기의 시각 · 사유 · 주문 ID로 조립하고, 주문 쪽은 {@link WishOrders}(주문 컨텍스트)로 읽는다. 주문은 보호자 이름으로 만들었으므로
     * 그 자녀의 보호자 회원으로 읽는다.
     */
    @Transactional(readOnly = true)
    public WishTimelineView getTimeline(WishActor actor, Long requestId) {
        WishRequest request = findUsable(actor, requestId);
        WishOrders.OrderTimeline order = request.getOrderId() == null ? null
            : wishOrders.findTimeline(childOf(request).memberId(), request.getOrderId()).orElse(null);
        return WishTimelineView.of(request, order);
    }

    /**
     * 바로 결제는 고른 줄마다 가격과 배송지가 있어야 한다. 구매 방법을 모르면 400.
     */
    private static Checkout checkoutOf(ApproveWishCommand command) {
        Checkout checkout;
        try {
            checkout = Checkout.valueOf(command.checkout());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(WishErrorCode.INVALID_APPROVAL);
        }
        if (command.items() == null || command.items().isEmpty()
            || command.items().stream().anyMatch(item -> item == null || item.wishItemId() == null)) {
            throw new BusinessException(WishErrorCode.INVALID_APPROVAL);
        }
        if (checkout == Checkout.ORDER && (command.shippingAddress() == null
            || command.items().stream().anyMatch(item -> item.unitPrice() == null))) {
            throw new BusinessException(WishErrorCode.INVALID_APPROVAL);
        }
        return checkout;
    }

    private void requireDecider(WishActor actor) {
        if (!wishPolicy.canDecide(actor)) {
            throw new BusinessException(WishErrorCode.WISH_REQUEST_DECIDE_FORBIDDEN);
        }
    }

    private WishRequest findUsable(WishActor actor, Long requestId) {
        WishRequest request = wishRequestRepository.findById(requestId)
            .orElseThrow(() -> new BusinessException(WishErrorCode.WISH_REQUEST_NOT_FOUND));
        requireUsableChild(actor, request.getChildId(), WishErrorCode.WISH_REQUEST_NOT_FOUND);
        return request;
    }

    private WishCatalog.Child childOf(WishRequest request) {
        return wishCatalog.findChild(request.getChildId())
            .orElseThrow(() -> new BusinessException(WishErrorCode.WISH_REQUEST_NOT_FOUND));
    }

    /**
     * 옵션 ID → 상품 ID(주문이 받아 둔 상품 정보). 없는 옵션은 빠진다(이벤트의 productId는 null).
     */
    private Map<Long, Long> productIdsOf(List<WishRequestLine> lines) {
        return wishCatalog.findProductOptions(lines.stream().map(WishRequestLine::getOptionId).toList()).stream()
            .collect(Collectors.toMap(ProductOption::optionId, ProductOption::productId));
    }

    private List<WishItem> itemsOf(WishRequest request) {
        return wishItemRepository.findAllByIdIn(request.wishItemIds());
    }

    private WishCatalog.Child requireUsableChild(WishActor actor, Long childId, WishErrorCode notFound) {
        return wishCatalog.findChild(childId)
            .filter(actor::canUse)
            .orElseThrow(() -> new BusinessException(notFound));
    }

    /**
     * 상품 · 판매자 복사본은 한 번씩 읽어 합친다(줄마다 조회하지 않음).
     */
    private List<WishRequestView> toViews(List<WishRequest> requests) {
        Set<Long> optionIds = requests.stream()
            .flatMap(request -> request.getLines().stream().map(WishRequestLine::getOptionId))
            .collect(Collectors.toSet());
        Map<Long, ProductOption> products = optionIds.isEmpty() ? Map.of()
            : wishCatalog.findProductOptions(optionIds).stream()
                .collect(Collectors.toMap(ProductOption::optionId, Function.identity()));
        Map<Long, String> sellerNames = sellerNamesOf(products.values());
        return requests.stream()
            .map(request -> new WishRequestView(request.getId(), request.getChildId(), request.getStatus().name(),
                request.getRequestedByType().name(), request.getMessage(), request.getRequestedAt(),
                nameOf(request.getCheckout()), request.getOrderId(), nameOf(request.getOrderResult()),
                nameOf(request.getRejectReason()), request.getDecidedAt(),
                request.getLines().stream()
                    .map(line -> toLine(line, products.get(line.getOptionId()), sellerNames))
                    .toList()))
            .toList();
    }

    private Map<Long, String> sellerNamesOf(Collection<ProductOption> products) {
        Set<Long> sellerIds = products.stream().map(ProductOption::sellerId).collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return wishCatalog.findSellerNames(sellerIds);
    }

    private static WishRequestView.Line toLine(WishRequestLine line, ProductOption product,
        Map<Long, String> sellerNames) {
        if (product == null) {
            return new WishRequestView.Line(line.getWishItemId(), line.getOptionId(), null, null, null, null, null,
                null, line.getRequestedQuantity(), line.getApprovedQuantity(), null, false, null);
        }
        return new WishRequestView.Line(line.getWishItemId(), line.getOptionId(), product.productId(),
            product.sellerId(), product.productName(), product.optionName(), product.size(),
            sellerNames.get(product.sellerId()), line.getRequestedQuantity(), line.getApprovedQuantity(),
            product.unitPrice(), product.purchasable(), product.saleStatus());
    }

    private static String nameOf(Enum<?> value) {
        return Objects.isNull(value) ? null : value.name();
    }
}
