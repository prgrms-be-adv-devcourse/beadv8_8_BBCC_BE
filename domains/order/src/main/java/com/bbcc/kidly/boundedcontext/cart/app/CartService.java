package com.bbcc.kidly.boundedcontext.cart.app;

import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors.Actor;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartEvents;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartReservationRepository;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCheckout;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartReservations;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem.OwnerType;
import com.bbcc.kidly.boundedcontext.cart.domain.CartReservation;
import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException;
import com.bbcc.kidly.boundedcontext.cart.domain.CartRules;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import com.bbcc.kidly.shared.order.event.CartItemPurchasedEvent;
import com.bbcc.kidly.shared.order.event.CartItemRemovedEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 담기 · 조회 · 수량 변경 · 삭제. 장바구니는 주인(#998: 회원 또는 로그인한 자녀) 한 명의 것이고, 남의 줄은 다루지 않는다:
 * 같은 가족(같은 보호자)의 줄이면 403(부모가 아이 장바구니를 다루는 것 포함), 그 밖이면 없는 줄(404)로 본다.
 * 아이 장바구니는 입을 아이가 그 아이로 고정이고, 그 아이 프로필 복사본이 없으면 담을 수 없다(CART-C-01).
 * 상품 · 판매자 · 자녀는 {@link CartCatalog}(주문 컨텍스트의 복사본)로 읽고, 주문은 읽지 않는다(#998).
 * 다른 컨텍스트에는 결제완료 정리({@link CartCleanup}) · 칸 예약({@link CartReservations}) ·
 * 주문 가능 확인({@link CartCheckout})을 내준다.
 * 담기에 성공하면 {@link CartItemAddedEvent}(#654), 사용자가 빼면 {@link CartItemRemovedEvent}(#709), 결제완료로 정리하면
 * {@link CartItemPurchasedEvent}(이번에 산 수량 · 주문 ID · 주문상품 ID, 예약 칸이면 예약 ID, #998)를 같은 트랜잭션에서 낸다({@link CartEvents}).
 * 수량 변경은 이벤트를 내지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CartService implements CartCleanup, CartReservations, CartCheckout {

    private final CartItemRepository cartItemRepository;
    private final CartReservationRepository reservationRepository;
    private final CartCatalog cartCatalog;
    private final CartEvents cartEvents;

    /**
     * 확인 순서: (자녀 주인이면 입을 아이 고정) → 옵션 없음 → 자기 상품 → 판매 중 아님 → 자녀 없음 · 남의 자녀 · 삭제된 자녀 → 수량.
     * 같은 줄(주인, 옵션, 입을 자녀)이 있으면 수량을 더한다(예약된 줄이면 409).
     * 성공하면(새 줄 · 합치기 모두) 같은 트랜잭션에서 담기 이벤트를 낸다.
     *
     * @param childId 입을 자녀. 회원은 고르지 않을 수 있고(null), 자녀 주인은 비우거나 자기 프로필이어야 한다(다르면 400)
     */
    public AddCartItemResult addItem(Actor owner, Long optionId, Long childId, int quantity) {
        Long wearer = childId;
        if (owner.type() == OwnerType.CHILD) {
            if (childId != null && !childId.equals(owner.id())) {
                throw new BusinessException(CartErrorCode.CHILD_CART_CHILD_FIXED);
            }
            wearer = owner.id();
        }
        ProductOption product = cartCatalog.findProductOption(optionId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.PRODUCT_OPTION_NOT_FOUND));
        try {
            CartRules.requireNotOwnProduct(owner.memberId(), product.sellerId());
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        if (!product.purchasable()) {
            throw new BusinessException(CartErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
        if (wearer != null) {
            requireOrderableChild(owner.memberId(), wearer);
        }

        Optional<CartItem> sameLine = cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(
            owner.type(), owner.id(), optionId, wearer);
        if (sameLine.isPresent()) {
            try {
                sameLine.get().addQuantity(quantity);
            } catch (CartRuleViolationException e) {
                throw CartErrors.toBusiness(e);
            }
            publishItemAdded(sameLine.get(), product, quantity);
            return new AddCartItemResult(toLine(sameLine.get(), product, sellerNameOf(product)), false);
        }
        CartItem created;
        try {
            created = cartItemRepository.save(CartItem.create(owner.type(), owner.id(), owner.memberId(), optionId,
                wearer, quantity));
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        publishItemAdded(created, product, quantity);
        return new AddCartItemResult(toLine(created, product, sellerNameOf(product)), true);
    }

    /**
     * @param quantity 이번에 담은 수량. 줄의 수량(담은 뒤)은 cartQuantity로 따로 담는다
     */
    private void publishItemAdded(CartItem item, ProductOption product, int quantity) {
        cartEvents.publish(new CartItemAddedEvent(UUID.randomUUID(), LocalDateTime.now(), new CartItemAddedEvent.Data(
            item.getId(), item.getMemberId(), item.getChildId(), product.productId(), item.getOptionId(), quantity,
            item.getQuantity(), item.getOwnerType().name(), item.getOwnerId())));
    }

    /**
     * 그 주인의 줄을 최근에 담은 것부터. 합계는 구매 가능한 줄만 더한다. 상품 · 판매자 복사본은 한 번씩 읽어 합친다.
     */
    @Transactional(readOnly = true)
    public CartView getCart(Actor owner) {
        List<CartItem> items = cartItemRepository.findAllByOwnerTypeAndOwnerIdOrderByIdDesc(owner.type(), owner.id());
        Map<Long, ProductOption> products = cartCatalog.findProductOptions(
                items.stream().map(CartItem::getOptionId).toList())
            .stream()
            .collect(Collectors.toMap(ProductOption::optionId, Function.identity()));
        Map<Long, String> sellerNames = sellerNamesOf(products.values());

        List<CartView.Line> lines = items.stream()
            .map(item -> {
                ProductOption product = products.get(item.getOptionId());
                return toLine(item, product, product == null ? null : sellerNames.get(product.sellerId()));
            })
            .toList();
        long totalAmount = lines.stream()
            .filter(CartView.Line::purchasable)
            .mapToLong(CartView.Line::lineAmount)
            .reduce(0L, Math::addExact);
        return new CartView(lines, totalAmount);
    }

    /**
     * 예약된 줄이면 409.
     */
    public CartView.Line changeQuantity(Actor owner, Long cartItemId, int quantity) {
        CartItem item = findOwnedItem(owner, cartItemId);
        try {
            item.changeQuantity(quantity);
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        ProductOption product = cartCatalog.findProductOption(item.getOptionId()).orElse(null);
        return toLine(item, product, sellerNameOf(product));
    }

    /**
     * 뺀 줄의 그때 수량으로 cart.item-removed를 낸다. 예약된 줄이면 409.
     */
    public void removeItem(Actor owner, Long cartItemId) {
        CartItem item = findOwnedItem(owner, cartItemId);
        try {
            item.requireRemovable();
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        cartItemRepository.delete(item);
        publishItemRemoved(item);
    }

    @Override
    public void clearOrdered(Long orderId, List<OrderedLine> lines) {
        Set<Long> deleted = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (OrderedLine line : lines) {
            // 주문에 기록된 칸(주문 때 requireOrderable로 확인됨)은 칸 ID로만 찾아 결제자 · 예약 상태와 상관없이 정리한다:
            // 결제자가 칸의 결제 회원과 달라도(보호자 2명 · 예약 허용 주체), 예약이 결제 전에 풀려도(철회 · 거절) 산 칸이 남지 않는다.
            // 칸 ID가 없으면(예전 방식) 구매자 회원 칸만
            Optional<CartItem> target = line.cartItemId() != null
                ? cartItemRepository.findById(line.cartItemId())
                : cartItemRepository.findByOwnerTypeAndOwnerIdAndOptionIdAndChildId(OwnerType.MEMBER, line.memberId(),
                    line.optionId(), line.childId());
            target.filter(item -> !deleted.contains(item.getId())).ifPresent(item -> {
                // 예약 칸이면 그 예약에 결제 정리 시각을 남긴다(칸이 남는 일부 결제도 예약한 쪽이 알 수 있게, #999)
                markPurchased(item.getReservationId(), now);
                // 산 수량만큼 뺀다. 수량을 모르는 예전 줄은 칸 전체를 산 것으로 본다
                int purchased = line.quantity() == null ? item.getQuantity() : line.quantity();
                if (item.deductPurchased(purchased) <= 0) {
                    deleted.add(item.getId());
                    cartItemRepository.delete(item);
                }
                publishItemPurchased(item, purchased, orderId, line.orderItemId());
            });
        }
    }

    private void markPurchased(String reservationId, LocalDateTime at) {
        if (reservationId != null) {
            reservationRepository.findAllByReservationId(reservationId).forEach(saved -> saved.markPurchased(at));
        }
    }

    @Override
    public int removeOwnerItems(String ownerType, Long ownerId) {
        OwnerType type = OwnerType.valueOf(ownerType);
        List<CartItem> items = cartItemRepository.findAllByOwnerTypeAndOwnerIdOrderByIdDesc(type, ownerId);
        items.forEach(cartItemRepository::delete);
        return items.size();
    }

    @Override
    public void reserve(List<Long> cartItemIds, List<Payer> payers, String reservationId,
        LocalDateTime validUntil) {
        if (cartItemIds == null || cartItemIds.isEmpty() || cartItemIds.stream().anyMatch(Objects::isNull)
            || payers == null || payers.isEmpty() || reservationId == null || reservationId.isBlank()
            || reservationId.length() > CartReservation.RESERVATION_ID_MAX) {
            throw new BusinessException(CartErrorCode.INVALID_RESERVATION);
        }
        List<Owner> parsedPayers = payers.stream().map(CartService::parse).distinct().toList();
        Set<Long> ids = new HashSet<>(cartItemIds);
        List<CartItem> items = cartItemRepository.findAllByIdIn(ids);
        if (items.size() != ids.size()) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
        try {
            items.forEach(item -> item.reserve(reservationId));
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        List<CartReservation> existing = reservationRepository.findAllByReservationId(reservationId);
        // 같은 예약의 행은 기한이 같아야 한다: 이미 있는 허용 주체도 이번 기한으로 맞춘다
        existing.forEach(saved -> saved.changeValidUntil(validUntil));
        parsedPayers.stream()
            .filter(payer -> existing.stream().noneMatch(saved -> saved.allows(payer.type(), payer.id())))
            .forEach(payer -> reservationRepository.save(
                CartReservation.allow(reservationId, payer.type(), payer.id(), validUntil)));
    }

    @Override
    public List<ReservedLine> reserveOwned(Payer owner, List<Long> cartItemIds, List<Payer> payers,
        String reservationId, LocalDateTime validUntil) {
        Owner parsedOwner = parse(owner);
        if (cartItemIds == null || cartItemIds.isEmpty() || cartItemIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(CartErrorCode.INVALID_RESERVATION);
        }
        Map<Long, CartItem> owned = cartItemRepository.findAllByIdIn(new HashSet<>(cartItemIds)).stream()
            .filter(item -> item.isOwnedBy(parsedOwner.type(), parsedOwner.id()))
            .collect(Collectors.toMap(CartItem::getId, Function.identity()));
        if (!owned.keySet().containsAll(cartItemIds)) {
            throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
        }
        reserve(cartItemIds, payers, reservationId, validUntil);
        return cartItemIds.stream()
            .map(owned::get)
            .map(item -> new ReservedLine(item.getId(), item.getOptionId(), item.getChildId(), item.getQuantity()))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> reservedCartItemIds(String reservationId) {
        if (reservationId == null) {
            return List.of();
        }
        return cartItemRepository.findAllByReservationId(reservationId).stream().map(CartItem::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchase(String reservationId) {
        if (reservationId == null) {
            return false;
        }
        return reservationRepository.findAllByReservationId(reservationId).stream()
            .anyMatch(CartReservation::isPurchased);
    }

    @Override
    public int updateValidUntil(List<ValidUntil> changes) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }
        Map<String, LocalDateTime> wanted = new HashMap<>();
        changes.stream().filter(change -> change.reservationId() != null)
            .forEach(change -> wanted.put(change.reservationId(), change.validUntil()));
        if (wanted.isEmpty()) {
            return 0;
        }
        Set<String> changed = new HashSet<>();
        for (CartReservation saved : reservationRepository.findAllByReservationIdIn(wanted.keySet())) {
            LocalDateTime validUntil = wanted.get(saved.getReservationId());
            if (!Objects.equals(saved.getValidUntil(), validUntil)) {
                saved.changeValidUntil(validUntil);
                changed.add(saved.getReservationId());
            }
        }
        return changed.size();
    }

    @Override
    public void release(String reservationId) {
        if (reservationId == null) {
            return;
        }
        cartItemRepository.findAllByReservationId(reservationId).forEach(CartItem::release);
        reservationRepository.findAllByReservationId(reservationId).forEach(reservationRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOrderable(Payer buyer, List<Line> lines) {
        Owner parsed = parse(buyer);
        LocalDateTime now = LocalDateTime.now();
        for (Line line : lines) {
            CartItem item = cartItemRepository.findById(line.cartItemId())
                .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
            Optional<CartReservation> allowed = allowedReservation(item, parsed.type(), parsed.id());
            boolean orderable = item.isOwnedBy(parsed.type(), parsed.id()) || allowed.isPresent();
            // 다른 가족의 칸이고 허용 주체도 아니면 없는 칸과 같게(존재를 숨김). 같은 가족인데 허용되지 않았으면 403
            if (!orderable && !item.getMemberId().equals(memberIdOf(parsed))) {
                throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
            }
            if (!orderable || !item.getOptionId().equals(line.optionId())
                || !Objects.equals(item.getChildId(), line.childId())) {
                throw new BusinessException(CartErrorCode.CART_ITEM_NOT_ORDERABLE);
            }
            // 남의 예약 칸을 허락받아 주문하는데 결제 가능 기한이 지났다(#1008). 자기 칸은 기한과 상관없다
            if (!item.isOwnedBy(parsed.type(), parsed.id()) && allowed.get().isExpiredAt(now)) {
                throw new BusinessException(CartErrorCode.RESERVATION_EXPIRED);
            }
            // 예약 칸(자기 칸이 아니라 허용받아 주문)은 담긴 수량까지만. 자기 칸은 지금처럼 수량을 보지 않는다
            if (!item.isOwnedBy(parsed.type(), parsed.id()) && line.quantity() > item.getQuantity()) {
                throw new BusinessException(CartErrorCode.RESERVED_QUANTITY_EXCEEDED);
            }
        }
    }

    // 주체의 결제 회원: 회원이면 그 회원. 자녀 주체는 보호자를 모르므로 가족 판단을 하지 않는다(null)
    private static Long memberIdOf(Owner owner) {
        return owner.type() == OwnerType.MEMBER ? owner.id() : null;
    }

    // 예약 칸이고 이 주체가 그 예약의 허용 주체면 그 허용 행
    private Optional<CartReservation> allowedReservation(CartItem item, OwnerType type, Long id) {
        if (!item.isReserved()) {
            return Optional.empty();
        }
        return reservationRepository.findAllByReservationId(item.getReservationId()).stream()
            .filter(payer -> payer.allows(type, id))
            .findFirst();
    }

    private void publishItemRemoved(CartItem item) {
        Long productId = cartCatalog.findProductOption(item.getOptionId()).map(ProductOption::productId).orElse(null);
        cartEvents.publish(new CartItemRemovedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new CartItemRemovedEvent.Data(item.getId(), item.getMemberId(), item.getChildId(), productId,
                item.getOptionId(), item.getQuantity(), item.getOwnerType().name(), item.getOwnerId())));
    }

    /**
     * @param purchased 이번에 산 수량(칸에 남은 수량이 아님)
     */
    private void publishItemPurchased(CartItem item, int purchased, Long orderId, Long orderItemId) {
        cartEvents.publish(new CartItemPurchasedEvent(UUID.randomUUID(), LocalDateTime.now(),
            new CartItemPurchasedEvent.Data(item.getId(), item.getOwnerType().name(), item.getOwnerId(),
                item.getMemberId(), item.getOptionId(), item.getChildId(), purchased, orderId, orderItemId,
                item.getReservationId())));
    }

    private void requireOrderableChild(Long memberId, Long childId) {
        cartCatalog.findChild(childId)
            .filter(child -> child.memberId().equals(memberId) && !child.deleted())
            .orElseThrow(() -> new BusinessException(CartErrorCode.CHILD_NOT_FOUND));
    }

    // 남의 줄: 같은 보호자(가족)면 403, 그 밖이면 없는 줄(404, 남의 장바구니가 있는지 알리지 않음)
    private CartItem findOwnedItem(Actor owner, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
        if (item.isOwnedBy(owner.type(), owner.id())) {
            return item;
        }
        if (item.getMemberId().equals(owner.memberId())) {
            throw new BusinessException(CartErrorCode.CART_FORBIDDEN);
        }
        throw new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND);
    }

    private static Owner parse(Payer payer) {
        if (payer == null || payer.id() == null || payer.type() == null) {
            throw new BusinessException(CartErrorCode.INVALID_RESERVATION);
        }
        try {
            return new Owner(OwnerType.valueOf(payer.type()), payer.id());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CartErrorCode.INVALID_RESERVATION);
        }
    }

    private record Owner(OwnerType type, Long id) {
    }

    private String sellerNameOf(ProductOption product) {
        if (product == null) {
            return null;
        }
        return cartCatalog.findSellerName(product.sellerId()).orElse(null);
    }

    private Map<Long, String> sellerNamesOf(Collection<ProductOption> products) {
        Set<Long> sellerIds = products.stream().map(ProductOption::sellerId).collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return cartCatalog.findSellerNames(sellerIds);
    }

    /**
     * 상품 복사본이 없으면 상품 필드 null, 구매 불가, 금액 0.
     */
    private CartView.Line toLine(CartItem item, ProductOption product, String sellerName) {
        if (product == null) {
            return new CartView.Line(item.getId(), item.getOptionId(), null, null, null, null, null, item.getChildId(),
                item.getQuantity(), null, false, 0L, null, item.isReserved());
        }
        return new CartView.Line(item.getId(), item.getOptionId(), product.productId(), product.sellerId(),
            product.productName(), product.optionName(), product.size(), item.getChildId(), item.getQuantity(),
            product.unitPrice(), product.purchasable(), Math.multiplyExact(product.unitPrice(), item.getQuantity()),
            sellerName, item.isReserved());
    }
}
