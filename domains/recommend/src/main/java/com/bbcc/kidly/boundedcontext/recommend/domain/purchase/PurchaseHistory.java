package com.bbcc.kidly.boundedcontext.recommend.domain.purchase;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 사이즈 추천에 쓰는 자녀의 지난 구매다. 마지막으로 산 사이즈 이름과, 최근 구매를 지금 상품과 견준 결과를 담는다.
 *
 * @param lastPurchasedSize 같은 칸에서 마지막으로 산 사이즈 이름이다. 산 적이 없으면 null이다
 */
public record PurchaseHistory(String lastPurchasedSize, List<PurchaseFit> purchases) {

    /** 구매를 보는 기간(개월)이다. 이보다 오래된 구매는 쓰지 않는다. */
    public static final int WINDOW_MONTHS = 12;

    /** 사이즈 추천에 넣는 최근 구매의 최대 개수다. */
    public static final int MAX_PURCHASES = 3;

    public PurchaseHistory {
        purchases = List.copyOf(requireNonNull(purchases, "purchases는 필수입니다"));
    }

    /**
     * 지난 구매를 지금 상품과 견준다.
     *
     * @param product 지금 보는 상품이다
     * @param productBrand 지금 상품의 브랜드다. 모르면 null이다
     * @param garments 지난 구매로 산 옷이다
     * @param cmPerMonth 자녀의 성장 속도(cm/월)다
     * @param today 개월 수를 셀 기준 날짜다
     */
    public record CompareInfo(FitProduct product, String productBrand, List<PurchasedGarment> garments,
                              BigDecimal cmPerMonth, LocalDate today) {
    }

    public static PurchaseHistory compare(CompareInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.product(), "product는 필수입니다");
        requireNonNull(info.garments(), "garments는 필수입니다");
        requireNonNull(info.cmPerMonth(), "cmPerMonth는 필수입니다");
        requireNonNull(info.today(), "today는 필수입니다");
        GarmentPart part = GarmentPart.ofCategoryPath(info.product().categoryPathNames());
        // 사이즈 무관 상품은 고를 사이즈가 없고, 모자 · 양말은 견줄 칸이 없어 지난 구매를 쓰지 않는다
        if (info.product().sizeFree() || part == null) {
            return new PurchaseHistory(null, List.of());
        }
        LocalDate windowStart = info.today().minusMonths(WINDOW_MONTHS);
        List<PurchasedGarment> samePart = new ArrayList<>();
        for (PurchasedGarment garment : info.garments()) {
            if (GarmentPart.ofCategoryPath(garment.categoryPathNames()) == part
                && isWithinWindow(garment, windowStart, info.today())) {
                samePart.add(garment);
            }
        }
        // 확정 시각이 늦은 구매가 앞에 오고, 시각이 같으면 구매 기록 ID가 큰 구매가 앞에 온다.
        samePart.sort(Comparator.comparing(PurchasedGarment::confirmedAt)
            .thenComparing(PurchasedGarment::purchaseRecordId).reversed());
        List<PurchaseFit> fits = new ArrayList<>();
        for (PurchasedGarment garment : samePart.subList(0, Math.min(MAX_PURCHASES, samePart.size()))) {
            fits.add(fitOf(info, garment));
        }
        String lastSize = fits.isEmpty() ? null : fits.getFirst().size();
        return new PurchaseHistory(lastSize, fits);
    }

    // 12개월 전 날짜부터 오늘까지(둘 다 포함) 확정한 구매만 쓴다. 오늘보다 뒤의 확정은 서비스 사이 시계 차이로만 생긴다.
    private static boolean isWithinWindow(PurchasedGarment garment, LocalDate windowStart, LocalDate today) {
        LocalDate confirmedOn = garment.confirmedAt().toLocalDate();
        return !confirmedOn.isBefore(windowStart) && !confirmedOn.isAfter(today);
    }

    private static PurchaseFit fitOf(CompareInfo info, PurchasedGarment garment) {
        int months = (int) Period.between(garment.confirmedAt().toLocalDate(), info.today()).toTotalMonths();
        boolean sameBrand = info.productBrand() != null && Objects.equals(info.productBrand(), garment.brand());
        BigDecimal growth = info.cmPerMonth().multiply(BigDecimal.valueOf(months)).setScale(1, RoundingMode.HALF_UP);
        List<ReferenceFit.Item> items = ReferenceFit.itemsOf(info.product(), garment.categoryId(),
            garment.measurements());
        return new PurchaseFit(garment.size(), months, sameBrand, growth, items);
    }
}
