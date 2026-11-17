package com.bbcc.kidly.boundedcontext.cart.app;

import com.bbcc.kidly.boundedcontext.cart.app.dto.CartView;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors.Actor;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartItemRepository;
import com.bbcc.kidly.boundedcontext.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 조회(#1508, CartService에서 분리): 주인의 줄 목록과 판매자 상호 · 합계. 담기 · 수량 변경 · 빼기 · 칸 예약 · 결제 후
 * 정리는 CartService다. 상품 · 판매자는 {@link CartCatalog}(주문 컨텍스트의 복사본)로 읽는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CartQueryService {

    private final CartItemRepository cartItemRepository;
    private final CartCatalog cartCatalog;

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
                return CartLines.toLine(item, product, product == null ? null : sellerNames.get(product.sellerId()));
            })
            .toList();
        long totalAmount = lines.stream()
            .filter(CartView.Line::purchasable)
            .mapToLong(CartView.Line::lineAmount)
            .reduce(0L, Math::addExact);
        return new CartView(lines, totalAmount);
    }


    private Map<Long, String> sellerNamesOf(Collection<ProductOption> products) {
        Set<Long> sellerIds = products.stream().map(ProductOption::sellerId).collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return cartCatalog.findSellerNames(sellerIds);
    }
}
