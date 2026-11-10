package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_BIRTH_DATE;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_CHANGED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.HomePreviewAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidateReader;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.support.HomePreviewCandidates;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaProductService;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

    private static ProductChangedEvent productAWithCategoryPath(List<ProductChangedEvent.CategoryPathEntry> path) {
        ProductChangedEvent.Data data = RecommendFixture.productAChangedEvent().data();
        return new ProductChangedEvent(UUID.randomUUID(), PRODUCT_CHANGED_AT, new ProductChangedEvent.Data(
            data.productId(), data.sellerId(), data.name(), data.categoryId(), data.status(), data.thumbnailUrl(),
            data.createdAt(), data.sizeFree(), data.options(), data.infoValues(), data.brand(), data.description(),
            data.sizeSpecs(), path));
    }
}
