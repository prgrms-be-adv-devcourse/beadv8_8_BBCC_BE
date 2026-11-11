package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_REGISTERED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_THUMBNAIL_URL;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendReplicaSellerFixture.BUSINESS_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.companion.support.CompanionCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.support.CompanionCandidates;
import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiProduct;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaSellerService;
import com.bbcc.kidly.boundedcontext.recommend.domain.activity.ActivityType;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 함께 사면 좋은 상품의 후보를 실제 PostgreSQL에서 읽어 규칙 순서로 정하는 동작을 확인한다.
 * 자녀 1은 키 115cm라, 110(105 ~ 115cm) · 120(115 ~ 125cm) 옵션이 있는 상품은 맞는 사이즈가 있다.
 */
@IntegrationTest
class CompanionCandidateReaderIntegrationTest {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private static final Long VIEWED_PRODUCT_ID = 7001L;

    private static final List<ProductChangedEvent.CategoryPathEntry> TOP = List.of(
        new ProductChangedEvent.CategoryPathEntry(1L, "의류"), new ProductChangedEvent.CategoryPathEntry(52L, "상의"));
    private static final List<ProductChangedEvent.CategoryPathEntry> BOTTOM = List.of(
        new ProductChangedEvent.CategoryPathEntry(1L, "의류"), new ProductChangedEvent.CategoryPathEntry(60L, "하의"));
    private static final List<ProductChangedEvent.CategoryPathEntry> OUTER = List.of(
        new ProductChangedEvent.CategoryPathEntry(1L, "의류"), new ProductChangedEvent.CategoryPathEntry(61L, "아우터"));

    private static final List<ProductChangedEvent.Option> FITTING_OPTIONS = List.of(
        RecommendFixture.productOption(1L, "110", 105, 115),
        RecommendFixture.productOption(2L, "120", 115, 125));

    @Autowired
    private CompanionCandidateReader reader;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Autowired
    private RecommendReplicaProductService replicaProductService;

    @Autowired
    private RecommendReplicaSellerService replicaSellerService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());
        replicaSellerService.applySellerChanged(RecommendReplicaSellerFixture.sellerApprovedEvent());
    }

    @Test
    @DisplayName("PAIR-05 판매 중이고 사이즈가 맞는 짝 종류 상품에서 장바구니 · 구매확정 상품을 빼고, 유사도와 짝 우선순위 점수 순으로 정한다")
    void readPairCandidatesInRuleOrder() {
        saveProduct(VIEWED_PRODUCT_ID, TOP, FITTING_OPTIONS, "키즈랩");
        // 상의의 짝은 바지(1), 치마(3/4), 아우터(2/4), 잡화(1/4) 순이다. 점수는 101 1.0, 102 0.7, 103 0.65다
        saveProduct(101L, BOTTOM, FITTING_OPTIONS, "데님하우스");
        saveProduct(102L, BOTTOM, FITTING_OPTIONS, null);
        saveProduct(103L, OUTER, FITTING_OPTIONS, null);
        saveProduct(104L, TOP, FITTING_OPTIONS, null);
        saveProduct(105L, BOTTOM, List.of(RecommendFixture.productOption(3L, "150", 145, 155)), null);
        saveProduct(106L, BOTTOM, FITTING_OPTIONS, null);
        saveProduct(107L, BOTTOM, FITTING_OPTIONS, null);
        saveEmbedding(VIEWED_PRODUCT_ID, 0);
        saveEmbedding(101L, 0);
        saveEmbedding(102L, 1);
        saveEmbedding(103L, 0);
        LocalDateTime yesterday = LocalDateTime.now(SERVICE_ZONE).minusDays(1);
        PreferenceTestData.insertActivity(jdbc, ActivityType.CART_ITEM_ADDED, MEMBER_1_ID, null, yesterday,
            106L, null, null, 1L);
        PreferenceTestData.insertPurchase(jdbc, CHILD_1_ID, 107L, yesterday);

        CompanionCandidates candidates = reader.read(MEMBER_1_ID, VIEWED_PRODUCT_ID, CHILD_1_ID);

        assertThat(candidates.ranked()).extracting(candidate -> candidate.product().productId())
            .containsExactly(101L, 102L, 103L);
        assertThat(candidates.ranked()).extracting(CompanionCandidate::categoryPathNames)
            .containsExactly(List.of("의류", "하의"), List.of("의류", "하의"), List.of("의류", "아우터"));
        assertThat(candidates.businessNames()).isEqualTo(Map.of(PRODUCT_A_SELLER_ID, BUSINESS_NAME));
        assertThat(candidates.aiRequest().viewed()).isEqualTo(
            new CompanionAiProduct(VIEWED_PRODUCT_ID, "상품 7001", List.of("의류", "상의"), "키즈랩"));
        assertThat(candidates.aiRequest().candidates()).containsExactly(
            new CompanionAiProduct(101L, "상품 101", List.of("의류", "하의"), "데님하우스"),
            new CompanionAiProduct(102L, "상품 102", List.of("의류", "하의"), null),
            new CompanionAiProduct(103L, "상품 103", List.of("의류", "아우터"), null));
    }

    // 판매자는 판매자 복사본이 있는 상품 A의 판매자로 두고, 판매 시작 시각은 모두 같게 둔다
    private void saveProduct(Long productId, List<ProductChangedEvent.CategoryPathEntry> categoryPath,
        List<ProductChangedEvent.Option> options, String brand) {
        Long leafCategoryId = categoryPath.getLast().id();
        replicaProductService.applyProductChanged(new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT,
            new ProductChangedEvent.Data(productId, PRODUCT_A_SELLER_ID, "상품 " + productId, leafCategoryId,
                ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, options, List.of(),
                brand, null, List.of(), categoryPath, false, PRODUCT_A_REGISTERED_AT)));
    }

    // 축 하나만 1인 단위 벡터를 넣는다. 같은 축이면 유사도 1, 다른 축이면 0이다
    private void saveEmbedding(Long productId, int axis) {
        jdbc.update("UPDATE recommend.replica_product SET embedding = CAST(? AS recommend.vector) WHERE product_id = ?",
            Arrays.toString(PreferenceTestData.unitVector(axis, 1f).values()), productId);
    }
}
