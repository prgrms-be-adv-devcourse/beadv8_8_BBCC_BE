package com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;

/**
 * 자녀의 몸 높이에 상품 사이즈를 대어 보고 착용 미리보기를 만든다.
 */
public class WearPreviewCalculator {

    private final BodyHeightTable table;

    public WearPreviewCalculator(BodyHeightTable table) {
        this.table = requireNonNull(table, "table은 필수입니다");
    }

    public Optional<WearPreview> preview(FitProduct product, ChildSizeInput child, LocalDate today) {
        long months = Period.between(child.birthDate(), today).toTotalMonths();
        BodyHeightRow ratios = table.ratios(child.gender(), months);
        GarmentPart garment = GarmentPart.ofCategoryPath(product.categoryPathNames());
        return Optional.of(new WearPreview(garment, bodyOf(child.heightCm(), ratios), List.of()));
    }

    private static WearPreview.Body bodyOf(double heightCm, BodyHeightRow ratios) {
        return new WearPreview.Body(
                roundToTenth(heightCm * ratios.neck()),
                roundToTenth(heightCm * ratios.shoulder()),
                roundToTenth(heightCm * ratios.waist()),
                roundToTenth(heightCm * ratios.hip()),
                roundToTenth(heightCm * ratios.crotch()),
                roundToTenth(heightCm * ratios.knee()),
                roundToTenth(heightCm * ratios.ankle()),
                roundToTenth(heightCm * ratios.arm()));
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
