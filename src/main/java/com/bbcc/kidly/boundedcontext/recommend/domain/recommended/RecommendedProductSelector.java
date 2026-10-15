package com.bbcc.kidly.boundedcontext.recommend.domain.recommended;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 상품 복사본 목록에서 아이에게 추천할 상품을 골라 추천 사이즈와 함께 돌려준다.
 */
@RequiredArgsConstructor
public class RecommendedProductSelector {

    private final SizeRecommender sizeRecommender;

    public List<RecommendedProduct> select(
            ChildSizeInput childSizeInput,
            List<RecommendReplicaProduct> products,
            LocalDate today
    ) {
        List<RecommendedProduct> selected = new ArrayList<>();
        for (RecommendReplicaProduct product : products) {
            SizeRecommendation recommendation =
                    sizeRecommender.recommend(childSizeInput, product.toProductSizes(), today);
            if (recommendation.getRecommendedSize() == null) {
                continue;
            }
            selected.add(new RecommendedProduct(
                    product.getProductId(),
                    product.getSellerId(),
                    product.getName(),
                    product.getThumbnailUrl(),
                    product.getMinPrice(),
                    recommendation.getRecommendedSize()
            ));
        }
        return selected;
    }
}
