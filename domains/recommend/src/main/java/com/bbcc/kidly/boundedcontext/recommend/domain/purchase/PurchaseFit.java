package com.bbcc.kidly.boundedcontext.recommend.domain.purchase;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import java.math.BigDecimal;
import java.util.List;

/**
 * 지난 구매 하나를 지금 상품과 견준 결과다.
 *
 * @param size 산 사이즈 이름이다
 * @param monthsAgo 산 뒤 지난 개월 수다
 * @param sameBrand 지금 상품과 산 상품의 브랜드가 같은지다
 * @param expectedGrowthCm 산 뒤 자란 것으로 예상하는 키(cm)다
 * @param items 지금 상품 사이즈별 실측에서 그때 산 옷 실측을 뺀 차이다
 */
public record PurchaseFit(String size, int monthsAgo, boolean sameBrand, BigDecimal expectedGrowthCm,
                          List<ReferenceFit.Item> items) {

    public PurchaseFit {
        requireNonNull(size, "size는 필수입니다");
        requireNonNull(expectedGrowthCm, "expectedGrowthCm은 필수입니다");
        items = List.copyOf(requireNonNull(items, "items는 필수입니다"));
    }
}
