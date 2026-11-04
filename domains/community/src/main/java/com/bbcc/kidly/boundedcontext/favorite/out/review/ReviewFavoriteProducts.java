package com.bbcc.kidly.boundedcontext.favorite.out.review;

import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteProducts;
import com.bbcc.kidly.boundedcontext.review.app.port.in.ProductCatalog;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 찜이 쓰는 상품 정보를 리뷰 컨텍스트의 상품 카탈로그(들어오는 포트)에서 묶어 읽어 옮겨 담는다. 리뷰 컨텍스트를 아는 것은 이 어댑터뿐이다.
 */
@Component
@RequiredArgsConstructor
public class ReviewFavoriteProducts implements FavoriteProducts {

    private final ProductCatalog productCatalog;

    @Override
    public Map<Long, ProductView> find(Collection<Long> productIds) {
        return productCatalog.find(productIds).values().stream()
            .collect(Collectors.toMap(ProductCatalog.ProductInfo::productId,
                info -> new ProductView(info.productId(), info.name(), info.thumbnailUrl(), info.displayStatus(),
                    info.saleEnded())));
    }
}
