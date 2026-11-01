package com.bbcc.kidly.boundedcontext.cart.app;

import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartEvents;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartOrders;
import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartCleanup;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException;
import com.bbcc.kidly.boundedcontext.cart.domain.CartRules;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.order.event.CartItemAddedEvent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 담기 · 조회 · 수량 변경 · 삭제. 다른 회원의 줄은 없는 줄로 본다.
 * 상품 · 판매자 · 자녀는 {@link CartCatalog}, 주문한 상품은 {@link CartOrders}로 읽는다(주문 컨텍스트, #641).
 * 결제완료 뒤 장바구니 정리는 {@link CartCleanup}으로 내준다. 담기에 성공하면 {@link CartItemAddedEvent}를 낸다(#654, {@link CartEvents}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CartService implements CartCleanup {

    private final CartItemRepository cartItemRepository;
    private final CartCatalog cartCatalog;
    private final CartOrders cartOrders;
    private final CartEvents cartEvents;

    /**
     * 확인 순서: 옵션 없음 → 자기 상품 → 판매 중 아님 → 자녀 없음 · 남의 자녀 · 삭제된 자녀 → 수량.
     * 같은 줄(회원, 옵션, 자녀)이 있으면 수량을 더한다. 성공하면(새 줄 · 합치기 모두) 같은 트랜잭션에서 담기 이벤트를 낸다.
     */
    public AddCartItemResult addItem(Long memberId, Long optionId, Long childId, int quantity) {
        ProductOption product = cartCatalog.findProductOption(optionId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.PRODUCT_OPTION_NOT_FOUND));
        try {
            CartRules.requireNotOwnProduct(memberId, product.sellerId());
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        if (!product.purchasable()) {
            throw new BusinessException(CartErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
        if (childId != null) {
            requireOrderableChild(memberId, childId);
        }

        Optional<CartItem> sameLine = cartItemRepository.findByMemberIdAndOptionIdAndChildId(
            memberId, optionId, childId);
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
            created = cartItemRepository.save(CartItem.create(memberId, optionId, childId, quantity));
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
            item.getQuantity())));
    }

    /**
     * 최근에 담은 줄부터. 합계는 구매 가능한 줄만 더한다. 상품 · 판매자 복사본은 한 번씩 읽어 합친다(줄마다 조회하지 않음).
     */
    @Transactional(readOnly = true)
    public CartView getCart(Long memberId) {
        List<CartItem> items = cartItemRepository.findAllByMemberIdOrderByIdDesc(memberId);
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

    public CartView.Line changeQuantity(Long memberId, Long cartItemId, int quantity) {
        CartItem item = findOwnedItem(memberId, cartItemId);
        try {
            item.changeQuantity(quantity);
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        ProductOption product = cartCatalog.findProductOption(item.getOptionId()).orElse(null);
        return toLine(item, product, sellerNameOf(product));
    }

    /**
     * 결제완료된 주문의 (회원, 옵션, 자녀)와 같은 장바구니 줄을 수량과 상관없이 통째로 지운다.
     */
    @Override
    public void clearOrdered(Long orderId) {
        cartOrders.findOrderedLines(orderId).forEach(line ->
            cartItemRepository.findByMemberIdAndOptionIdAndChildId(line.memberId(), line.optionId(), line.childId())
                .ifPresent(cartItemRepository::delete));
    }

    public void removeItem(Long memberId, Long cartItemId) {
        cartItemRepository.delete(findOwnedItem(memberId, cartItemId));
    }

    private void requireOrderableChild(Long memberId, Long childId) {
        cartCatalog.findChild(childId)
            .filter(child -> child.memberId().equals(memberId) && !child.deleted())
            .orElseThrow(() -> new BusinessException(CartErrorCode.CHILD_NOT_FOUND));
    }

    private CartItem findOwnedItem(Long memberId, Long cartItemId) {
        return cartItemRepository.findByIdAndMemberId(cartItemId, memberId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
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
                item.getQuantity(), null, false, 0L, null);
        }
        return new CartView.Line(item.getId(), item.getOptionId(), product.productId(), product.sellerId(),
            product.productName(), product.optionName(), product.size(), item.getChildId(), item.getQuantity(),
            product.unitPrice(), product.purchasable(), Math.multiplyExact(product.unitPrice(), item.getQuantity()),
            sellerName);
    }
}
