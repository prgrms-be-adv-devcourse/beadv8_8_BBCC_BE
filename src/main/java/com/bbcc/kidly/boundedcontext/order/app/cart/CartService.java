package com.bbcc.kidly.boundedcontext.order.app.cart;

import com.bbcc.kidly.boundedcontext.order.app.cart.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.order.app.cart.dto.CartView;
import com.bbcc.kidly.boundedcontext.order.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.OrderErrorCode;
import com.bbcc.kidly.boundedcontext.order.domain.cart.CartItem;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 담기 · 조회 · 수량 변경 · 삭제. 다른 회원의 줄은 없는 줄로 본다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CartService {

    /**
     * 한 줄 수량 상한(담기 · 수량 변경 · 주문하기 공통, #253). 요청 DTO의 @Max(99)와 같은 값.
     * 담기 합산 상한은 담기 경로가 이 서비스 하나뿐이라 도메인(CartItem)을 바꾸지 않고 여기서 막는다.
     */
    static final int MAX_QUANTITY = 99;

    private final CartItemRepository cartItemRepository;
    private final ProductReplicaRepository productReplicaRepository;
    private final ChildReplicaRepository childReplicaRepository;
    private final OrderRepository orderRepository;

    /**
     * 확인 순서: 옵션 없음 → 자기 상품 → 판매 중 아님 → 자녀 없음 · 남의 자녀 · 삭제된 자녀 → 수량.
     * 같은 줄(회원, 옵션, 자녀)이 있으면 수량을 더한다.
     */
    public AddCartItemResult addItem(Long memberId, Long optionId, Long childId, int quantity) {
        ReplicaProduct product = productReplicaRepository.findByOptionId(optionId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.PRODUCT_OPTION_NOT_FOUND));
        if (product.isSoldBy(memberId)) {
            throw new BusinessException(OrderErrorCode.OWN_PRODUCT);
        }
        if (!product.isPurchasable()) {
            throw new BusinessException(OrderErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
        if (childId != null) {
            requireOrderableChild(memberId, childId);
        }

        Optional<CartItem> sameLine = cartItemRepository.findByMemberIdAndOptionIdAndChildId(
            memberId, optionId, childId);
        if (sameLine.isPresent()) {
            if (sameLine.get().getQuantity() + quantity > MAX_QUANTITY) {
                throw new BusinessException(OrderErrorCode.INVALID_QUANTITY);
            }
            sameLine.get().addQuantity(quantity);
            return new AddCartItemResult(toLine(sameLine.get(), product), false);
        }
        CartItem created = cartItemRepository.save(
            CartItem.create(memberId, optionId, childId, quantity));
        return new AddCartItemResult(toLine(created, product), true);
    }

    /**
     * 최근에 담은 줄부터. 합계는 구매 가능한 줄만 더한다.
     */
    @Transactional(readOnly = true)
    public CartView getCart(Long memberId) {
        List<CartItem> items = cartItemRepository.findAllByMemberIdOrderByIdDesc(memberId);
        Map<Long, ReplicaProduct> products = productReplicaRepository.findAllByOptionIdIn(
                items.stream().map(CartItem::getOptionId).toList())
            .stream()
            .collect(Collectors.toMap(ReplicaProduct::getOptionId, Function.identity()));

        List<CartView.Line> lines = items.stream()
            .map(item -> toLine(item, products.get(item.getOptionId())))
            .toList();
        long totalAmount = lines.stream()
            .filter(CartView.Line::purchasable)
            .mapToLong(CartView.Line::lineAmount)
            .reduce(0L, Math::addExact);
        return new CartView(lines, totalAmount);
    }

    public CartView.Line changeQuantity(Long memberId, Long cartItemId, int quantity) {
        CartItem item = findOwnedItem(memberId, cartItemId);
        item.changeQuantity(quantity);
        return toLine(item, productReplicaRepository.findByOptionId(item.getOptionId()).orElse(null));
    }

    /**
     * 결제완료된 주문의 (회원, 옵션, 자녀)와 같은 장바구니 줄을 수량과 상관없이 통째로 지운다.
     */
    public void clearOrdered(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> order.getItems().forEach(item ->
            cartItemRepository.findByMemberIdAndOptionIdAndChildId(
                    order.getMemberId(),
                    item.getProduct().optionId(),
                    item.getChild() == null ? null : item.getChild().childId())
                .ifPresent(cartItemRepository::delete)));
    }

    public void removeItem(Long memberId, Long cartItemId) {
        cartItemRepository.delete(findOwnedItem(memberId, cartItemId));
    }

    private void requireOrderableChild(Long memberId, Long childId) {
        childReplicaRepository.findByChildId(childId)
            .filter(child -> child.isOwnedBy(memberId) && !child.isDeleted())
            .orElseThrow(() -> new BusinessException(OrderErrorCode.CHILD_NOT_FOUND));
    }

    private CartItem findOwnedItem(Long memberId, Long cartItemId) {
        return cartItemRepository.findByIdAndMemberId(cartItemId, memberId)
            .orElseThrow(() -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND));
    }

    /**
     * 상품 복사본이 없으면 상품 필드 null, 구매 불가, 금액 0.
     */
    private CartView.Line toLine(CartItem item, ReplicaProduct product) {
        if (product == null) {
            return new CartView.Line(item.getId(), item.getOptionId(), null, null, null, null, null, item.getChildId(),
                item.getQuantity(), null, false, 0L);
        }
        return new CartView.Line(item.getId(), item.getOptionId(), product.getProductId(), product.getSellerId(),
            product.getProductName(), product.getOptionName(), product.getSize(), item.getChildId(), item.getQuantity(),
            product.unitPrice(), product.isPurchasable(), Math.multiplyExact(product.unitPrice(), item.getQuantity()));
    }
}
