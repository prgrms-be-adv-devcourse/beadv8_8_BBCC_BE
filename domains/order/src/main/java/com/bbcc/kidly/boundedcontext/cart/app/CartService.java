package com.bbcc.kidly.boundedcontext.cart.app;

import com.bbcc.kidly.boundedcontext.cart.api.CartApi;
import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import com.bbcc.kidly.boundedcontext.cart.domain.CartRuleViolationException;
import com.bbcc.kidly.boundedcontext.order.api.OrderApi;
import com.bbcc.kidly.boundedcontext.order.api.ProductOptionView;
import com.bbcc.kidly.boundedcontext.order.api.ReplicaApi;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 담기 · 조회 · 수량 변경 · 삭제. 다른 회원의 줄은 없는 줄로 본다.
 * 상품 · 판매자 · 자녀 복사본과 주문은 주문 컨텍스트의 api로 읽는다(#617). 결제완료 뒤 장바구니 정리는 {@link CartApi}로 내준다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CartService implements CartApi {

    /**
     * 한 줄 수량 상한(담기 · 수량 변경 · 주문하기 공통, #253). 요청 DTO의 @Max(99)와 같은 값.
     * 담기 합산 상한은 담기 경로가 이 서비스 하나뿐이라 도메인(CartItem)을 바꾸지 않고 여기서 막는다.
     */
    static final int MAX_QUANTITY = 99;

    private final CartItemRepository cartItemRepository;
    private final ReplicaApi replicaApi;
    private final OrderApi orderApi;

    /**
     * 확인 순서: 옵션 없음 → 자기 상품 → 판매 중 아님 → 자녀 없음 · 남의 자녀 · 삭제된 자녀 → 수량.
     * 같은 줄(회원, 옵션, 자녀)이 있으면 수량을 더한다.
     */
    public AddCartItemResult addItem(Long memberId, Long optionId, Long childId, int quantity) {
        ProductOptionView product = replicaApi.findProductOption(optionId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.PRODUCT_OPTION_NOT_FOUND));
        if (product.sellerId().equals(memberId)) {
            throw new BusinessException(CartErrorCode.OWN_PRODUCT);
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
            if (sameLine.get().getQuantity() + quantity > MAX_QUANTITY) {
                throw new BusinessException(CartErrorCode.INVALID_QUANTITY);
            }
            try {
                sameLine.get().addQuantity(quantity);
            } catch (CartRuleViolationException e) {
                throw CartErrors.toBusiness(e);
            }
            return new AddCartItemResult(toLine(sameLine.get(), product, sellerNameOf(product)), false);
        }
        CartItem created;
        try {
            created = cartItemRepository.save(CartItem.create(memberId, optionId, childId, quantity));
        } catch (CartRuleViolationException e) {
            throw CartErrors.toBusiness(e);
        }
        return new AddCartItemResult(toLine(created, product, sellerNameOf(product)), true);
    }

    /**
     * 최근에 담은 줄부터. 합계는 구매 가능한 줄만 더한다. 상품 · 판매자 복사본은 한 번씩 읽어 합친다(줄마다 조회하지 않음).
     */
    @Transactional(readOnly = true)
    public CartView getCart(Long memberId) {
        List<CartItem> items = cartItemRepository.findAllByMemberIdOrderByIdDesc(memberId);
        Map<Long, ProductOptionView> products = replicaApi.findProductOptions(
                items.stream().map(CartItem::getOptionId).toList())
            .stream()
            .collect(Collectors.toMap(ProductOptionView::optionId, Function.identity()));
        Map<Long, String> sellerNames = sellerNamesOf(products.values());

        List<CartView.Line> lines = items.stream()
            .map(item -> {
                ProductOptionView product = products.get(item.getOptionId());
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
        ProductOptionView product = replicaApi.findProductOption(item.getOptionId()).orElse(null);
        return toLine(item, product, sellerNameOf(product));
    }

    /**
     * 결제완료된 주문의 (회원, 옵션, 자녀)와 같은 장바구니 줄을 수량과 상관없이 통째로 지운다.
     */
    @Override
    public void clearOrdered(Long orderId) {
        orderApi.findOrder(orderId).ifPresent(order -> order.items().forEach(item ->
            cartItemRepository.findByMemberIdAndOptionIdAndChildId(
                    order.memberId(),
                    item.optionId(),
                    item.child() == null ? null : item.child().childId())
                .ifPresent(cartItemRepository::delete)));
    }

    public void removeItem(Long memberId, Long cartItemId) {
        cartItemRepository.delete(findOwnedItem(memberId, cartItemId));
    }

    private void requireOrderableChild(Long memberId, Long childId) {
        replicaApi.findChild(childId)
            .filter(child -> child.memberId().equals(memberId) && !child.deleted())
            .orElseThrow(() -> new BusinessException(CartErrorCode.CHILD_NOT_FOUND));
    }

    private CartItem findOwnedItem(Long memberId, Long cartItemId) {
        return cartItemRepository.findByIdAndMemberId(cartItemId, memberId)
            .orElseThrow(() -> new BusinessException(CartErrorCode.CART_ITEM_NOT_FOUND));
    }

    private String sellerNameOf(ProductOptionView product) {
        if (product == null) {
            return null;
        }
        return replicaApi.findSellerName(product.sellerId()).orElse(null);
    }

    private Map<Long, String> sellerNamesOf(Collection<ProductOptionView> products) {
        Set<Long> sellerIds = products.stream().map(ProductOptionView::sellerId).collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return replicaApi.findSellerNames(sellerIds);
    }

    /**
     * 상품 복사본이 없으면 상품 필드 null, 구매 불가, 금액 0.
     */
    private CartView.Line toLine(CartItem item, ProductOptionView product, String sellerName) {
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
