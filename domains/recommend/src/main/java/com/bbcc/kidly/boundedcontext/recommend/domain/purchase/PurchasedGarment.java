package com.bbcc.kidly.boundedcontext.recommend.domain.purchase;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 자녀가 지난 구매로 산 옷 한 벌이다. 구매 기록과 산 상품 복사본에서 찾은 그 사이즈의 실측을 함께 담는다.
 *
 * @param categoryPathNames 산 상품의 대분류부터 말단까지의 카테고리 이름이다
 * @param brand 산 상품의 브랜드다. 모르면 null이다
 * @param measurements 산 사이즈의 실측이다. 키는 실측 항목 코드, 값은 cm이고, 실측을 찾지 못했으면 빈 Map이다
 */
public record PurchasedGarment(Long purchaseRecordId, String size, LocalDateTime confirmedAt, Long categoryId,
                               List<String> categoryPathNames, String brand,
                               Map<String, BigDecimal> measurements) {

    public PurchasedGarment {
        requireNonNull(purchaseRecordId, "purchaseRecordId는 필수입니다");
        requireNonNull(size, "size는 필수입니다");
        requireNonNull(confirmedAt, "confirmedAt은 필수입니다");
        categoryPathNames = List.copyOf(requireNonNull(categoryPathNames, "categoryPathNames는 필수입니다"));
        measurements = Map.copyOf(requireNonNull(measurements, "measurements는 필수입니다"));
    }
}
