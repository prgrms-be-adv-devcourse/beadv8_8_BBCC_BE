package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ReferenceGarmentFixture {

    private ReferenceGarmentFixture() {
    }

    public static ReferenceGarment top(Long childProfileId) {
        return ReferenceGarment.create(childProfileId, ReferenceGarmentSpec.of(GarmentPart.TOP, 52L,
            List.of(new GarmentCategoryPathEntry(1L, "의류"), new GarmentCategoryPathEntry(52L, "상의")),
            Map.of("TOTAL_LENGTH", new BigDecimal("40.5"), "CHEST_WIDTH", new BigDecimal("42.0"))));
    }

    public static ReferenceGarment bottom(Long childProfileId) {
        return ReferenceGarment.create(childProfileId, ReferenceGarmentSpec.of(GarmentPart.BOTTOM, 60L,
            List.of(new GarmentCategoryPathEntry(1L, "의류"), new GarmentCategoryPathEntry(60L, "하의")),
            Map.of("TOTAL_LENGTH", new BigDecimal("60.0"), "WAIST_WIDTH", new BigDecimal("30.0"))));
    }
}
