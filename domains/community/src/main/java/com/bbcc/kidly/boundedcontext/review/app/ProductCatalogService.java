package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReplicaProducts;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductCatalog;
import com.bbcc.kidly.boundedcontext.review.domain.DisplayStatus;
import com.bbcc.kidly.boundedcontext.review.domain.ReplicaProduct;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 복사본을 상품 ID 묶음으로 한 번에 읽어 화면 정보로 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCatalogService implements ProductCatalog {

    private final ReplicaProducts replicaProducts;

    @Override
    public Map<Long, ProductInfo> find(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return replicaProducts.findAllByProductIdIn(productIds).stream()
            .map(ProductCatalogService::toInfo)
            .collect(Collectors.toMap(ProductInfo::productId, Function.identity()));
    }

    private static ProductInfo toInfo(ReplicaProduct replica) {
        return new ProductInfo(replica.getProductId(), replica.getName(), replica.getThumbnailUrl(),
            replica.getDisplayStatus().name(), replica.getDisplayStatus() == DisplayStatus.SALE_ENDED);
    }
}
