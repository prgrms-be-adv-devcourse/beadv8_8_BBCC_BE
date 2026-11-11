package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.CHILD_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.MEMBER_1_ID;
import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationDraft;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.IntegrationTest;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사이즈 추천의 AI 입력을 확인한다.
 * 자녀 1은 2019-01-01생 남아(키 115cm, 몸무게 20kg, 체형 보통, 선호 핏 여유)이고, 상품 A는 110(105~115cm) · 120(115~125cm)이다.
 * 상품 A의 이벤트 fixture에는 실측이 없어서, 실측이 있는 복사본을 리포지토리로 바로 저장한다.
 */
@IntegrationTest
class SizeRecommendationReaderIntegrationTest {

    @Autowired
    private SizeRecommendationReader reader;

    @Autowired
    private RecommendReplicaProductRepository replicaProductRepository;

    @Autowired
    private RecommendReplicaChildService replicaChildService;

    @Test
    @DisplayName("AI 입력에 자녀 개월 수 · 키 · 몸무게 · 체형 · 선호 핏 · 판정 체형, 사이즈별 권장 키와 실측, 규칙 결과를 담는다")
    void buildAiQuestion() {
        replicaProductRepository.save(RecommendFixture.productAReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        SizeAiQuestion question = draft.aiQuestion();
        assertThat(question.child()).isEqualTo(new SizeAiQuestion.Child(
            ageMonthsToday(), 115.0, 20.0, BodyType.NORMAL, PreferredFit.LOOSE, BodyType.NORMAL));
        assertThat(question.sizes()).containsExactlyInAnyOrder(
            new SizeAiQuestion.SizeLine("110", 105, 115,
                Map.of("TOTAL_LENGTH", new BigDecimal("42"), "CHEST_WIDTH", new BigDecimal("34"))),
            new SizeAiQuestion.SizeLine("120", 115, 125,
                Map.of("TOTAL_LENGTH", new BigDecimal("45"), "CHEST_WIDTH", new BigDecimal("36"))));
        assertThat(question.ruleResult())
            .isEqualTo(new SizeAiQuestion.RuleResult("120", "ROOMY", List.of("110", "120"), List.of()));
        assertThat(question.referenceFit()).isNull();
        assertThat(question.growthSpeed()).isNull();
    }

    @Test
    @DisplayName("지난 AI 답을 찾는 키는 상품 · 자녀 · 개월 수 · 키 · 몸무게 · 체형 · 선호 핏 · 프롬프트 버전 1로 만든다")
    void buildAiKeyInput() {
        replicaProductRepository.save(RecommendFixture.productAReplica());
        replicaChildService.applyChildChanged(RecommendFixture.childChangedEvent());

        SizeRecommendationDraft draft = reader.read(MEMBER_1_ID, PRODUCT_A_ID, CHILD_1_ID);

        AiResponseKey.Input keyInput = draft.aiKeyInput();
        assertThat(keyInput.productId()).isEqualTo(PRODUCT_A_ID);
        assertThat(keyInput.childProfileId()).isEqualTo(CHILD_1_ID);
        assertThat(keyInput.ageMonths()).isEqualTo(ageMonthsToday());
        assertThat(keyInput.heightCm()).isEqualByComparingTo("115");
        assertThat(keyInput.weightKg()).isEqualByComparingTo("20");
        assertThat(keyInput.bodyType()).isEqualTo(BodyType.NORMAL);
        assertThat(keyInput.preferredFit()).isEqualTo(PreferredFit.LOOSE);
        assertThat(keyInput.candidateProductIds()).isNull();
        assertThat(keyInput.cartProductIds()).isNull();
        assertThat(keyInput.promptVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("AI 입력에는 자녀 이름 · 생년월일 · 회원 ID를 담는 칸이 없다")
    void aiQuestionHasNoPersonalFields() {
        List<String> names = new ArrayList<>();
        for (Class<?> type : List.of(SizeAiQuestion.class, SizeAiQuestion.Child.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                names.add(component.getName());
            }
        }

        assertThat(names).doesNotContain("name", "birthDate", "memberId", "childProfileId");
    }

    private static int ageMonthsToday() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return (int) Period.between(LocalDate.of(2019, 1, 1), today).toTotalMonths();
    }
}
