package com.bbcc.kidly.boundedcontext.recommend.domain.growth;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.ArrayList;
import java.util.List;

/**
 * 아이의 키와 성장 속도로 상품의 사이즈별 입을 수 있는 기간을 계산한다.
 */
public class WearablePeriodCalculator {

    private final GirthRatioTable girthRatioTable;

    public WearablePeriodCalculator(GirthRatioTable girthRatioTable) {
        this.girthRatioTable = requireNonNull(girthRatioTable, "girthRatioTable은 필수입니다");
    }

    /**
     * @param referenceFit 기준 옷이 없으면 null이다
     */
    public record WearInfo(ProductSizes sizes, List<String> categoryPathNames, Gender gender, long ageMonths,
            double heightCm, double cmPerMonth, ReferenceFit referenceFit) {
    }

    public List<WearablePeriod> calculate(WearInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.sizes(), "sizes는 필수입니다");
        requireNonNull(info.categoryPathNames(), "categoryPathNames는 필수입니다");
        requireNonNull(info.gender(), "gender는 필수입니다");
        List<WearablePeriod> periods = new ArrayList<>();
        for (SizeRange range : info.sizes().sizeRanges()) {
            int lengthMonths = (int) Math.floor((range.maxHeightCm() - info.heightCm()) / info.cmPerMonth());
            Integer widthMonths = widthMonths(info, range.size());
            periods.add(new WearablePeriod(range.size(), lengthMonths, widthMonths, lengthMonths));
        }
        return periods;
    }

    /**
     * 기준 옷보다 큰 너비를 몸이 채우는 데 걸리는 개월 수다. 너비 항목이 여럿이면 가장 짧은 값을 쓰고, 없으면 null이다.
     * 너비 항목은 단면이라 몸 둘레의 절반만큼 커진다고 본다.
     */
    private Integer widthMonths(WearInfo info, String size) {
        if (info.referenceFit() == null) {
            return null;
        }
        Integer shortest = null;
        for (ReferenceFit.Item item : info.referenceFit().items()) {
            GirthPart part = GirthPart.ofWidthCode(item.code());
            if (part == null) {
                continue;
            }
            for (ReferenceFit.SizeDiff diff : item.sizes()) {
                if (!diff.size().equals(size)) {
                    continue;
                }
                double ratio = girthRatioTable.ratio(info.gender(), info.ageMonths(), part);
                int months = (int) Math.floor(diff.diffCm().doubleValue() / (info.cmPerMonth() * ratio / 2));
                if (shortest == null || months < shortest) {
                    shortest = months;
                }
            }
        }
        return shortest;
    }
}
