package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_BIRTH_DATE;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_BRAND;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_CATEGORY_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.productOption;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidates;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 홈 맛보기 후보를 읽을 때 외부 AI에 줄 자녀 값과 후보 칸을 복사본에서 채우는지 실제 PostgreSQL로 확인한다.
 */
@IntegrationTest
class HomePreviewCandidateReaderIntegrationTest {

    @Autowired
    private HomePreviewCandidateReader candidateReader;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("외부 AI에 줄 값으로 자녀의 개월 수 · 키 · 몸무게 · 체형 · 선호 핏과 후보의 카테고리 경로 이름을 채운다")
    void fillAiRequestFromReplicas() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        replicaProductService.applyProductChanged(productAWithCategoryPath(List.of(
            new ProductChangedEvent.CategoryPathEntry(1L, "의류"),
            new ProductChangedEvent.CategoryPathEntry(52L, "상의"))));

        HomePreviewCandidates candidates = candidateReader.read(MEMBER_1_ID, CHILD_1_ID);

        assertThat(candidates.childProfileId()).isEqualTo(CHILD_1_ID);
        HomePreviewAiRequest.Child child = candidates.aiRequest().child();
        long expectedMonths = ChronoUnit.MONTHS.between(CHILD_1_BIRTH_DATE, LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(child.ageMonths()).isEqualTo((int) expectedMonths);
        assertThat(child.heightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
        assertThat(child.weightKg()).isEqualByComparingTo(new BigDecimal("20.0"));
        assertThat(child.bodyType()).isEqualTo(BodyType.NORMAL);
        assertThat(child.preferredFit()).isEqualTo(PreferredFit.LOOSE);
        assertThat(candidates.aiRequest().candidates())
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.productId()).isEqualTo(PRODUCT_A_ID);
                assertThat(candidate.categoryPath()).containsExactly("의류", "상의");
            });
    }

    @Test
    @DisplayName("HOME-07 후보마다 최근 30일 활동 · 최근 3개월 구매로 만든 근거 라벨과 판매 중인 색상 · 상품 정보(소재 · 설명)를 채운다")
    void fillEvidenceColorsAndProductInfo() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        replicaProductService.applyProductChanged(productAWithColorsAndInfo());
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        for (int i = 0; i < 4; i++) {
            insertActivity(ActivityType.SEARCH, now.minusDays(1), PRODUCT_A_CATEGORY_ID, null, null);
            insertActivity(ActivityType.SEARCH, now.minusDays(1), null, PRODUCT_A_BRAND, null);
        }
        insertActivity(ActivityType.CART_ITEM_ADDED, now.minusDays(2), PRODUCT_A_CATEGORY_ID, null, 1L);
        for (int i = 0; i < 3; i++) {
            insertActivity(ActivityType.SEARCH, now.minusDays(40), null, PRODUCT_A_BRAND, null);
        }
        PreferenceTestData.insertProduct(jdbc, 901L, 60L, PRODUCT_A_BRAND, null);
        PreferenceTestData.insertPurchase(jdbc, CHILD_1_ID, 901L, now.minusMonths(1));
        PreferenceTestData.insertProduct(jdbc, 902L, PRODUCT_A_CATEGORY_ID, "다른 브랜드", null);
        PreferenceTestData.insertPurchase(jdbc, CHILD_1_ID, 902L, now.minusMonths(4));

        HomePreviewCandidates candidates = candidateReader.read(MEMBER_1_ID, CHILD_1_ID);

        assertThat(candidates.aiRequest().today()).isEqualTo(now.toLocalDate());
        assertThat(candidates.aiRequest().candidates())
            .filteredOn(candidate -> candidate.productId().equals(PRODUCT_A_ID))
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.evidence()).containsExactly(
                    "자주 찾아본 카테고리: 상의", "장바구니에 담은 카테고리: 상의", "산 적 있는 브랜드: 키들리");
                assertThat(candidate.colors()).containsExactly("네이비", "아이보리");
                assertThat(candidate.productInfo()).isEqualTo("MAIN_MATERIAL: 면 100% / 설명: 부드러운 기모 맨투맨이에요");
            });
    }

    private void insertActivity(ActivityType type, LocalDateTime occurredAt, Long categoryId, String brand,
        Long cartItemId) {
        PreferenceTestData.insertActivity(jdbc, type, MEMBER_1_ID, CHILD_1_ID, occurredAt, null, categoryId, brand,
            cartItemId);
    }

    private static ProductChangedEvent productAWithColorsAndInfo() {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        List<ProductChangedEvent.Option> options = List.of(
            optionWithColor(1L, "110", "네이비"), optionWithColor(2L, "120", "아이보리"));
        return new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT, new ProductChangedEvent.Data(
            data.productId(), data.sellerId(), data.name(), data.categoryId(), data.status(), data.thumbnailUrl(),
            data.createdAt(), data.sizeFree(), options,
            List.of(new ProductChangedEvent.AttributeEntry("MAIN_MATERIAL", 1L, "면 100%")), PRODUCT_A_BRAND,
            "부드러운 기모 맨투맨이에요", data.sizeSpecs(), List.of(
                new ProductChangedEvent.CategoryPathEntry(1L, "의류"),
                new ProductChangedEvent.CategoryPathEntry(PRODUCT_A_CATEGORY_ID, "상의"))));
    }

    private static ProductChangedEvent.Option optionWithColor(Long optionId, String size, String color) {
        ProductChangedEvent.Option option = productOption(optionId, size, 105, 125);
        return new ProductChangedEvent.Option(option.optionId(), option.optionName(), option.size(), option.price(),
            option.minHeightCm(), option.maxHeightCm(), option.soldOut(), option.saleStatus(),
            List.of(new ProductChangedEvent.AttributeEntry("COLOR", 900L + optionId, color)));
    }

    private static ProductChangedEvent productAWithCategoryPath(List<ProductChangedEvent.CategoryPathEntry> path) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT, new ProductChangedEvent.Data(
            data.productId(), data.sellerId(), data.name(), data.categoryId(), data.status(), data.thumbnailUrl(),
            data.createdAt(), data.sizeFree(), data.options(), data.infoValues(), data.brand(), data.description(),
            data.sizeSpecs(), path));
    }
}
