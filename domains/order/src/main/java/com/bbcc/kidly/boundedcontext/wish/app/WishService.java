package com.bbcc.kidly.boundedcontext.wish.app;

import com.bbcc.kidly.boundedcontext.wish.app.dto.AddWishItemResult;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog.ProductOption;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishItemRepository;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.AddedBy;
import com.bbcc.kidly.boundedcontext.wish.domain.WishItem.Status;
import com.bbcc.kidly.boundedcontext.wish.domain.WishRuleViolationException;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조르기(부모 · 자녀 공용 장바구니) 담기 · 보기 · 빼기(#640). 쓸 수 없는 자녀(남의 자녀 · 삭제된 자녀 · 없는 자녀)와
 * 그 자녀의 줄은 없는 것으로 본다. 상품 표시 · 구매 가능 판단은 장바구니와 같다({@link WishCatalog}: 주문 컨텍스트의 상품 · 판매자 · 자녀 복사본, #641).
 * 합치기 · 최대 줄 수 · 빼기 권한은 {@link WishPolicy}가 정한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishService {

    /**
     * 한 줄 수량 상한. 장바구니와 같은 값이고 요청 DTO의 @Max(99)와 같다.
     */
    static final int MAX_QUANTITY = 99;

    private final WishItemRepository wishItemRepository;
    private final WishCatalog wishCatalog;
    private final WishPolicy wishPolicy;

    /**
     * 확인 순서(장바구니와 같음): 옵션 없음 → 자기 상품(부모) → 판매 중 아님 → 자녀 → 수량. 그 뒤 정책: 같은 옵션 합치기 → 최대 줄 수.
     */
    public AddWishItemResult add(WishActor actor, Long childId, Long optionId, int quantity) {
        ProductOption product = wishCatalog.findProductOption(optionId)
            .orElseThrow(() -> new BusinessException(WishErrorCode.PRODUCT_OPTION_NOT_FOUND));
        if (actor.type() == AddedBy.PARENT && product.sellerId().equals(actor.id())) {
            throw new BusinessException(WishErrorCode.OWN_PRODUCT);
        }
        if (!product.purchasable()) {
            throw new BusinessException(WishErrorCode.PRODUCT_NOT_PURCHASABLE);
        }
        requireUsableChild(actor, childId, WishErrorCode.CHILD_NOT_FOUND);

        Optional<WishItem> sameOption = wishPolicy.mergesSameOption()
            ? wishItemRepository.findFirstByChildIdAndOptionIdAndStatusOrderByIdAsc(childId, optionId, Status.ACTIVE)
            : Optional.empty();
        if (sameOption.isPresent()) {
            if (sameOption.get().getQuantity() + quantity > MAX_QUANTITY) {
                throw new BusinessException(WishErrorCode.INVALID_QUANTITY);
            }
            try {
                sameOption.get().addQuantity(quantity);
            } catch (WishRuleViolationException e) {
                throw WishErrors.toBusiness(e);
            }
            return new AddWishItemResult(toLine(sameOption.get(), product, sellerNameOf(product)), false);
        }
        if (wishItemRepository.countByChildIdAndStatus(childId, Status.ACTIVE) >= wishPolicy.maxLines()) {
            throw new BusinessException(WishErrorCode.WISH_ITEM_LIMIT_EXCEEDED);
        }
        WishItem created;
        try {
            created = wishItemRepository.save(WishItem.create(childId, optionId, quantity, actor.type(), actor.id()));
        } catch (WishRuleViolationException e) {
            throw WishErrors.toBusiness(e);
        }
        return new AddWishItemResult(toLine(created, product, sellerNameOf(product)), true);
    }

    /**
     * 최근에 담은 줄부터. 합계는 구매 가능한 줄만 더한다. 상품 · 판매자 복사본은 한 번씩 읽어 합친다(줄마다 조회하지 않음).
     */
    @Transactional(readOnly = true)
    public WishView getWishes(WishActor actor, Long childId) {
        requireUsableChild(actor, childId, WishErrorCode.CHILD_NOT_FOUND);
        List<WishItem> items = wishItemRepository.findAllByChildIdAndStatusOrderByIdDesc(childId, Status.ACTIVE);
        Map<Long, ProductOption> products = wishCatalog.findProductOptions(
                items.stream().map(WishItem::getOptionId).toList())
            .stream()
            .collect(Collectors.toMap(ProductOption::optionId, Function.identity()));
        Map<Long, String> sellerNames = sellerNamesOf(products.values());

        List<WishView.Line> lines = items.stream()
            .map(item -> {
                ProductOption product = products.get(item.getOptionId());
                return toLine(item, product, product == null ? null : sellerNames.get(product.sellerId()));
            })
            .toList();
        long totalAmount = lines.stream()
            .filter(WishView.Line::purchasable)
            .mapToLong(WishView.Line::lineAmount)
            .reduce(0L, Math::addExact);
        return new WishView(childId, lines, totalAmount);
    }

    /**
     * 쓸 수 없는 자녀의 줄은 없는 줄(404), 쓸 수 있는 자녀의 줄이라도 정책이 막으면 403.
     */
    public void remove(WishActor actor, Long wishItemId) {
        WishItem item = wishItemRepository.findByIdAndStatus(wishItemId, Status.ACTIVE)
            .orElseThrow(() -> new BusinessException(WishErrorCode.WISH_ITEM_NOT_FOUND));
        requireUsableChild(actor, item.getChildId(), WishErrorCode.WISH_ITEM_NOT_FOUND);
        if (!wishPolicy.canRemove(actor, item)) {
            throw new BusinessException(WishErrorCode.WISH_ITEM_REMOVE_FORBIDDEN);
        }
        wishItemRepository.delete(item);
    }

    private void requireUsableChild(WishActor actor, Long childId, WishErrorCode notFound) {
        wishCatalog.findChild(childId)
            .filter(actor::canUse)
            .orElseThrow(() -> new BusinessException(notFound));
    }

    private String sellerNameOf(ProductOption product) {
        return wishCatalog.findSellerName(product.sellerId()).orElse(null);
    }

    private Map<Long, String> sellerNamesOf(Collection<ProductOption> products) {
        Set<Long> sellerIds = products.stream().map(ProductOption::sellerId).collect(Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Map.of();
        }
        return wishCatalog.findSellerNames(sellerIds);
    }

    /**
     * 상품 복사본이 없으면 상품 필드 null, 구매 불가, 금액 0(장바구니와 같음).
     */
    private WishView.Line toLine(WishItem item, ProductOption product, String sellerName) {
        if (product == null) {
            return new WishView.Line(item.getId(), item.getChildId(), item.getOptionId(), null, null, null, null, null,
                item.getQuantity(), null, false, 0L, null, item.getAddedByType().name(), item.getAddedAt());
        }
        return new WishView.Line(item.getId(), item.getChildId(), item.getOptionId(), product.productId(),
            product.sellerId(), product.productName(), product.optionName(), product.size(), item.getQuantity(),
            product.unitPrice(), product.purchasable(), Math.multiplyExact(product.unitPrice(), item.getQuantity()),
            sellerName, item.getAddedByType().name(), item.getAddedAt());
    }
}
