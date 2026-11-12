package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationDraft;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사이즈 추천이 자녀의 지난 구매를 읽어 쓰는지 실제 DB에서 확인한다.
 * 상품 A는 상의(카테고리 52, 브랜드 「키들리」)이고 110 · 120에 총장 · 가슴단면 실측이 있다.
 */
@IntegrationTest
class SizeRecommendationPurchaseIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

    private static final Long OTHER_CHILD_ID = 12L;

    // 지난번에 산 상의다. 상품 A와 카테고리 · 브랜드가 같다
    private static final Long BOUGHT_TOP_ID = 71L;

    private static final Long BOUGHT_PANTS_ID = 72L;

    // 추천에 상품 복사본이 없는 상품이다
    private static final Long PRODUCT_WITHOUT_REPLICA_ID = 73L;

    @Autowired
    private SizeRecommendationReader reader;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private PurchaseRecordRepository purchaseRecordRepository;

    @Test
    @DisplayName("PUR-01 지난 구매 사이즈는 그 자녀가 12개월 안에 같은 칸에서 마지막으로 구매확정한 사이즈이고, 다른 칸 · 12개월 밖 · 다른 자녀의 구매는 보지 않는다")
    void readLastPurchasedSizeOfSamePart() {
        saveProductAndChild();
        replicaProductRepository.save(boughtProduct(BOUGHT_TOP_ID, 52L, "상의"));
        replicaProductRepository.save(boughtProduct(BOUGHT_PANTS_ID, 60L, "바지"));
        savePurchase(1L, CHILD_1_ID, BOUGHT_TOP_ID, "110", NOW.minusMonths(5));
        savePurchase(2L, CHILD_1_ID, BOUGHT_PANTS_ID, "130", NOW.minusMonths(1));
        savePurchase(3L, CHILD_1_ID, BOUGHT_TOP_ID, "120", NOW.minusMonths(12).minusDays(1));
        savePurchase(4L, OTHER_CHILD_ID, BOUGHT_TOP_ID, "130", NOW.minusDays(1));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        assertThat(draft.lastPurchasedSize()).isEqualTo("110");
    }

    @Test
    @DisplayName("PUR-02 · PUR-04 AI 질문에는 12개월 안 같은 칸 구매를 산 옷의 실측과 견준 결과로 넣고, 상품 복사본이 없는 구매는 칸을 몰라 뺀다")
    void putPurchasesIntoAiQuestion() {
        saveProductAndChild();
        replicaProductRepository.save(boughtProduct(BOUGHT_TOP_ID, 52L, "상의"));
        savePurchase(1L, CHILD_1_ID, BOUGHT_TOP_ID, "110", NOW.minusMonths(5));
        savePurchase(2L, CHILD_1_ID, BOUGHT_TOP_ID, "120", NOW.minusMonths(12).minusDays(1));
        savePurchase(3L, CHILD_1_ID, PRODUCT_WITHOUT_REPLICA_ID, "120", NOW.minusMonths(2));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        List<PurchaseFit> purchases = draft.aiQuestion().purchases();
        assertThat(purchases).hasSize(1);
        PurchaseFit purchase = purchases.getFirst();
        assertThat(purchase.size()).isEqualTo("110");
        assertThat(purchase.monthsAgo()).isEqualTo(5);
        assertThat(purchase.sameBrand()).isTrue();
        // 상품 A 110 · 120(총장 42 · 45, 가슴단면 34 · 36)에서 산 110(총장 43, 가슴단면 33)을 뺀다
        assertThat(purchase.items())
            .usingRecursiveFieldByFieldElementComparator(cmComparison())
            .containsExactly(
                new ReferenceFit.Item("TOTAL_LENGTH", new BigDecimal("43"), List.of(
                    new ReferenceFit.SizeDiff("110", new BigDecimal("42"), new BigDecimal("-1")),
                    new ReferenceFit.SizeDiff("120", new BigDecimal("45"), new BigDecimal("2")))),
                new ReferenceFit.Item("CHEST_WIDTH", new BigDecimal("33"), List.of(
                    new ReferenceFit.SizeDiff("110", new BigDecimal("34"), new BigDecimal("1")),
                    new ReferenceFit.SizeDiff("120", new BigDecimal("36"), new BigDecimal("3")))));
    }

    @Test
    @DisplayName("PUR-05 같은 칸 구매가 생기면 지난 AI 답을 찾는 키가 달라져, 구매를 모르던 예전 답을 다시 쓰지 않는다")
    void aiKeyChangesWithPurchase() {
        saveProductAndChild();
        replicaProductRepository.save(boughtProduct(BOUGHT_TOP_ID, 52L, "상의"));
        AiResponseKey withoutPurchase =
            AiResponseKey.of(reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID).aiKeyInput());

        savePurchase(1L, CHILD_1_ID, BOUGHT_TOP_ID, "110", NOW.minusMonths(5));
        AiResponseKey withPurchase =
            AiResponseKey.of(reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID).aiKeyInput());

        assertThat(withPurchase).isNotEqualTo(withoutPurchase);
    }

    // 만 7세 남아는 표 값 0.50cm/월이라 5개월 동안 2.5cm 클 것으로 본다
    @Test
    @DisplayName("PUR-05 답 검증 값에는 산 옷 실측 · 실측 차이 · 산 뒤 개월 수 · 예상 성장량도 모은다")
    void collectPurchaseNumbers() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEventBornOn(
            NOW.toLocalDate().minusYears(7).minusMonths(6)));
        replicaProductRepository.save(boughtProduct(BOUGHT_TOP_ID, 52L, "상의"));
        savePurchase(1L, CHILD_1_ID, BOUGHT_TOP_ID, "110", NOW.minusMonths(5));

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        List<String> given = new ArrayList<>();
        for (BigDecimal number : draft.aiEvidence().givenNumbers()) {
            given.add(number.stripTrailingZeros().toPlainString());
        }
        assertThat(given).contains("43", "33", "5", "2.5");
    }

    private void saveProductAndChild() {
        replicaProductRepository.save(RecommendFixture.productATopReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
    }

    private void savePurchase(Long orderItemId, Long childProfileId, Long productId, String size,
        LocalDateTime confirmedAt) {
        purchaseRecordRepository.save(PurchaseRecord.create(new PurchaseRecord.RecordInfo(orderItemId, 900L,
            MEMBER_1_ID, childProfileId, productId, 800L + orderItemId, size, confirmedAt)));
    }

    private static RecursiveComparisonConfiguration cmComparison() {
        return RecursiveComparisonConfiguration.builder()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .build();
    }

    // 110 · 120에 총장 · 가슴단면 실측이 있는 상품 복사본이다
    private static RecommendReplicaProduct boughtProduct(Long productId, Long categoryId, String categoryName) {
        List<RecommendReplicaProduct.OptionInfo> options = List.of(
            new RecommendReplicaProduct.OptionInfo("110", 105, 115, 19000L, false, OptionSaleStatus.ON_SALE),
            new RecommendReplicaProduct.OptionInfo("120", 115, 125, 19000L, false, OptionSaleStatus.ON_SALE));
        List<RecommendReplicaProduct.SizeSpecInfo> specs = List.of(
            new RecommendReplicaProduct.SizeSpecInfo("110", 105, 115,
                Map.of("TOTAL_LENGTH", new BigDecimal("43"), "CHEST_WIDTH", new BigDecimal("33"))),
            new RecommendReplicaProduct.SizeSpecInfo("120", 115, 125,
                Map.of("TOTAL_LENGTH", new BigDecimal("46"), "CHEST_WIDTH", new BigDecimal("35"))));
        return RecommendReplicaProduct.create(new RecommendReplicaProduct.ChangeInfo(productId,
            RecommendFixture.PRODUCT_A_SELLER_ID, categoryId, "지난번 옷", ProductStatus.ON_SALE, null,
            NOW.minusMonths(13), false, options, RecommendFixture.PRODUCT_A_BRAND, null, specs, List.of(),
            NOW.minusMonths(13),
            List.of(new RecommendReplicaProduct.CategoryPathEntryInfo(1L, "의류"),
                new RecommendReplicaProduct.CategoryPathEntryInfo(categoryId, categoryName))));
    }
}
