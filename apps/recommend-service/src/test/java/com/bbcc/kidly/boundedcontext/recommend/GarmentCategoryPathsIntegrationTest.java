package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment.GarmentCategoryPaths;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class GarmentCategoryPathsIntegrationTest {

    private static final Long CLOTHING_ID = 30L;

    private static final Long TOP_ID = 7L;

    private static final Long BOTTOM_ID = 70L;

    private static final Long OTHER_PRODUCT_ID = 32L;

    @Autowired
    private GarmentCategoryPaths garmentCategoryPaths;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Test
    @DisplayName("상품 복사본의 카테고리 경로에 있는 카테고리를 찾으면 그 경로 전체가 대분류부터 말단 순서로 돌아온다")
    void findFullPathInOrder() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath("의류"), RecommendFixture.PRODUCT_CHANGED_AT);

        List<GarmentCategoryPathEntry> path = garmentCategoryPaths.findPathOf(TOP_ID);

        assertThat(path).extracting(GarmentCategoryPathEntry::categoryId, GarmentCategoryPathEntry::categoryName)
            .containsExactly(tuple(CLOTHING_ID, "의류"), tuple(TOP_ID, "상의"));
    }

    @Test
    @DisplayName("같은 말단 카테고리의 상품이 둘이면 더 늦게 반영된 상품의 경로 하나만 돌아온다")
    void findPathOfLatelyAppliedProduct() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath("의류"), RecommendFixture.PRODUCT_CHANGED_AT);
        applyProduct(OTHER_PRODUCT_ID, topPath("신규 의류"), RecommendFixture.PRODUCT_CHANGED_AT.plusDays(1));

        assertThat(garmentCategoryPaths.findPathOf(TOP_ID))
            .extracting(GarmentCategoryPathEntry::categoryName).containsExactly("신규 의류", "상의");
    }

    @Test
    @DisplayName("같은 말단 카테고리의 두 상품이 같은 시각에 반영됐으면 상품 ID가 큰 상품의 경로가 돌아온다")
    void findPathOfLargerProductIdWhenAppliedAtSameTime() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath("의류"), RecommendFixture.PRODUCT_CHANGED_AT);
        applyProduct(OTHER_PRODUCT_ID, topPath("신규 의류"), RecommendFixture.PRODUCT_CHANGED_AT);

        assertThat(garmentCategoryPaths.findPathOf(TOP_ID))
            .extracting(GarmentCategoryPathEntry::categoryName).containsExactly("신규 의류", "상의");
    }

    @ParameterizedTest
    @ValueSource(longs = {30L, 999L})
    @DisplayName("말단 카테고리가 아니거나 어떤 상품에도 없는 카테고리면 빈 목록이다")
    void findNothingForNonLeafOrUnknownCategory(Long categoryId) {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath("의류"), RecommendFixture.PRODUCT_CHANGED_AT);

        assertThat(garmentCategoryPaths.findPathOf(categoryId)).isEmpty();
    }

    @Test
    @DisplayName("FIT-01 찾은 경로를 기준 옷 값에 넘기면 상의 · 하의 카테고리로 상의 · 하의 기준 옷이 만들어진다")
    void pathBuildsReferenceGarmentSpec() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath("의류"), RecommendFixture.PRODUCT_CHANGED_AT);
        applyProduct(OTHER_PRODUCT_ID, bottomPath(), RecommendFixture.PRODUCT_CHANGED_AT);

        List<GarmentCategoryPathEntry> topPath = garmentCategoryPaths.findPathOf(TOP_ID);
        List<GarmentCategoryPathEntry> bottomPath = garmentCategoryPaths.findPathOf(BOTTOM_ID);

        assertThatCode(() -> ReferenceGarmentSpec.of(GarmentPart.TOP, TOP_ID, topPath,
            Map.of("TOTAL_LENGTH", new BigDecimal("40.5"), "CHEST_WIDTH", new BigDecimal("42.0"))))
            .doesNotThrowAnyException();
        assertThatCode(() -> ReferenceGarmentSpec.of(GarmentPart.BOTTOM, BOTTOM_ID, bottomPath,
            Map.of("TOTAL_LENGTH", new BigDecimal("60.0"), "WAIST_WIDTH", new BigDecimal("30.0"))))
            .doesNotThrowAnyException();
    }

    private static List<ProductChangedEvent.CategoryPathEntry> topPath(String rootName) {
        return List.of(new ProductChangedEvent.CategoryPathEntry(CLOTHING_ID, rootName),
            new ProductChangedEvent.CategoryPathEntry(TOP_ID, "상의"));
    }

    private static List<ProductChangedEvent.CategoryPathEntry> bottomPath() {
        return List.of(new ProductChangedEvent.CategoryPathEntry(CLOTHING_ID, "의류"),
            new ProductChangedEvent.CategoryPathEntry(BOTTOM_ID, "하의"));
    }

    private void applyProduct(Long productId, List<ProductChangedEvent.CategoryPathEntry> categoryPath,
        LocalDateTime occurredAt) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        Long leafCategoryId = categoryPath.get(categoryPath.size() - 1).id();
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(),
            occurredAt, new ProductChangedEvent.Data(productId, data.sellerId(),
            data.name(), leafCategoryId, data.status(), data.thumbnailUrl(), data.createdAt(), data.sizeFree(),
            data.options(), data.infoValues(), data.brand(), data.description(), data.sizeSpecs(), categoryPath)));
    }
}
