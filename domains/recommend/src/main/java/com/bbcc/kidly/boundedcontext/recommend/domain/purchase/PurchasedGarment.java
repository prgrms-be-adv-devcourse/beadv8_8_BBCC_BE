package com.bbcc.kidly.boundedcontext.recommend.domain.purchase;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.ReviewFit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 자녀가 지난 구매로 산 옷 한 벌이다. 구매 기록과 산 상품 복사본에서 찾은 그 사이즈의 실측을 함께 담는다.
 * 산 옷 목록 API의 {@code PurchasedGarmentRow}와 달리, 지금 상품과 견줄 칸 · 실측을 담는다.
 *
 * @param categoryId 산 상품의 말단 카테고리 ID다. 산 상품 복사본이 없으면 null이다
 * @param categoryPathNames 산 상품의 대분류부터 말단까지의 카테고리 이름이다
 * @param brand 산 상품의 브랜드다. 모르면 null이다
 * @param measurements 산 사이즈의 실측이다. 키는 실측 항목 코드, 값은 cm이고, 실측을 찾지 못했으면 빈 Map이다
 * @param reviewFit 이 구매(주문상품)에 남긴 공개 리뷰의 사이즈 핏이다. 리뷰가 없거나 핏을 남기지 않았거나 숨긴 리뷰면 null이다
 */
public record PurchasedGarment(Long purchaseRecordId, String size, LocalDateTime confirmedAt, Long categoryId,
                               List<String> categoryPathNames, String brand,
                               Map<String, BigDecimal> measurements, ReviewFit reviewFit) {

    public PurchasedGarment {
        requireNonNull(purchaseRecordId, "purchaseRecordId는 필수입니다");
        requireNonNull(size, "size는 필수입니다");
        requireNonNull(confirmedAt, "confirmedAt은 필수입니다");
        categoryPathNames = List.copyOf(requireNonNull(categoryPathNames, "categoryPathNames는 필수입니다"));
        measurements = Map.copyOf(requireNonNull(measurements, "measurements는 필수입니다"));
    }
}
