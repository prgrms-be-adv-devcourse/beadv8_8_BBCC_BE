package com.bbcc.kidly.boundedcontext.wish.out.order;

import com.bbcc.kidly.boundedcontext.order.app.port.in.ReplicaLookup;
import com.bbcc.kidly.boundedcontext.order.app.port.in.ReplicaLookup.ProductOptionView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishCatalog;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 상품 옵션 · 판매자 상호 · 자녀를 주문 컨텍스트의 복사본 조회 포트로 읽는다(#641). 같은 앱 안의 동기 호출이라 부르는 쪽 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishCatalogAdapter implements WishCatalog {

    private final ReplicaLookup replicaLookup;

    @Override
    public Optional<ProductOption> findProductOption(Long optionId) {
        return replicaLookup.findProductOption(optionId).map(WishCatalogAdapter::toOption);
    }

    @Override
    public List<ProductOption> findProductOptions(Collection<Long> optionIds) {
        return replicaLookup.findProductOptions(optionIds).stream().map(WishCatalogAdapter::toOption).toList();
    }

    @Override
    public Optional<String> findSellerName(Long sellerId) {
        return replicaLookup.findSellerName(sellerId);
    }

    @Override
    public Map<Long, String> findSellerNames(Collection<Long> sellerIds) {
        return replicaLookup.findSellerNames(sellerIds);
    }

    @Override
    public Optional<Child> findChild(Long childId) {
        return replicaLookup.findChild(childId)
            .map(child -> new Child(child.childId(), child.memberId(), child.deleted()));
    }

    private static ProductOption toOption(ProductOptionView view) {
        return new ProductOption(view.optionId(), view.productId(), view.sellerId(), view.productName(),
            view.optionName(), view.size(), view.unitPrice(), view.purchasable());
    }
}
