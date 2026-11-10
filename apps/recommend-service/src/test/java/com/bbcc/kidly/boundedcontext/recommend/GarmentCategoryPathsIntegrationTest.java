package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment.GarmentCategoryPaths;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 기준 옷의 세부 종류 카테고리를 상품 복사본의 카테고리 경로에서 찾는 조회를 실제 DB로 확인한다.
 */
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

    // ID와 이름이 순서대로 커지지 않는 경로로, 값의 크기로 정렬해서는 통과하지 못하게 한다
    @Test
    @DisplayName("FIT-01 상품 복사본의 카테고리 경로에 있는 카테고리를 찾으면 그 경로 전체가 대분류부터 말단 순서로 돌아온다")
    void findFullPathInOrder() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath());

        List<GarmentCategoryPathEntry> path = garmentCategoryPaths.findPathOf(TOP_ID);

        assertThat(path).extracting(GarmentCategoryPathEntry::categoryId, GarmentCategoryPathEntry::categoryName)
            .containsExactly(tuple(CLOTHING_ID, "의류"), tuple(TOP_ID, "상의"));
    }

    @Test
    @DisplayName("FIT-01 같은 카테고리의 상품이 둘이어도 경로는 하나만 돌아온다")
    void findOnePathWhenManyProductsShareCategory() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath());
        applyProduct(OTHER_PRODUCT_ID, topPath());

        assertThat(garmentCategoryPaths.findPathOf(TOP_ID))
            .extracting(GarmentCategoryPathEntry::categoryId).containsExactly(CLOTHING_ID, TOP_ID);
    }

    @Test
    @DisplayName("FIT-01 중간 카테고리 ID로 찾으면 그 상품의 경로 전체가 돌아오고 판단은 하지 않는다")
    void findPathByMiddleCategory() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath());

        List<GarmentCategoryPathEntry> path = garmentCategoryPaths.findPathOf(CLOTHING_ID);

        assertThat(path).extracting(GarmentCategoryPathEntry::categoryId).containsExactly(CLOTHING_ID, TOP_ID);
    }

    @Test
    @DisplayName("FIT-01 어떤 상품의 경로에도 없는 카테고리면 빈 목록이다")
    void findNothingForUnknownCategory() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath());

        assertThat(garmentCategoryPaths.findPathOf(999L)).isEmpty();
    }

    @Test
    @DisplayName("FIT-01 찾은 경로를 기준 옷 값에 넘기면 상의 · 하의 카테고리로 상의 · 하의 기준 옷이 만들어진다")
    void pathBuildsReferenceGarmentSpec() {
        applyProduct(RecommendFixture.PRODUCT_A_ID, topPath());
        applyProduct(OTHER_PRODUCT_ID, bottomPath());

        ReferenceGarmentSpec top = ReferenceGarmentSpec.of(GarmentPart.TOP, TOP_ID,
            garmentCategoryPaths.findPathOf(TOP_ID),
            Map.of("TOTAL_LENGTH", new BigDecimal("40.5"), "CHEST_WIDTH", new BigDecimal("42.0")));
        ReferenceGarmentSpec bottom = ReferenceGarmentSpec.of(GarmentPart.BOTTOM, BOTTOM_ID,
            garmentCategoryPaths.findPathOf(BOTTOM_ID),
            Map.of("TOTAL_LENGTH", new BigDecimal("60.0"), "WAIST_WIDTH", new BigDecimal("30.0")));

        assertThat(top.categoryId()).isEqualTo(TOP_ID);
        assertThat(bottom.categoryId()).isEqualTo(BOTTOM_ID);
    }

    private static List<ProductChangedEvent.CategoryPathEntry> topPath() {
        return List.of(new ProductChangedEvent.CategoryPathEntry(CLOTHING_ID, "의류"),
            new ProductChangedEvent.CategoryPathEntry(TOP_ID, "상의"));
    }

    private static List<ProductChangedEvent.CategoryPathEntry> bottomPath() {
        return List.of(new ProductChangedEvent.CategoryPathEntry(CLOTHING_ID, "의류"),
            new ProductChangedEvent.CategoryPathEntry(BOTTOM_ID, "하의"));
    }

    private void applyProduct(Long productId, List<ProductChangedEvent.CategoryPathEntry> categoryPath) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(),
            RecommendFixture.PRODUCT_CHANGED_AT, new ProductChangedEvent.Data(productId, data.sellerId(),
            data.name(), data.categoryId(), data.status(), data.thumbnailUrl(), data.createdAt(), data.sizeFree(),
            data.options(), data.infoValues(), data.brand(), data.description(), data.sizeSpecs(), categoryPath)));
    }
}
