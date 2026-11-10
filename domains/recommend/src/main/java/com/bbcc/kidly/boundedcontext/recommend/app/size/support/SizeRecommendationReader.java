package com.bbcc.kidly.boundedcontext.recommend.app.size.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSizeSpec;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이즈 추천에 필요한 복사본을 트랜잭션 안에서 읽고, 규칙 계산과 AI 입력까지 만들어 초안으로 돌려준다.
 * AI 호출은 트랜잭션 밖에서 해야 해서 서비스가 아니라 이 클래스가 읽기를 맡는다.
 */
@Component
public class SizeRecommendationReader {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PROMPT_VERSION = 1;

    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendReplicaChildRepository replicaChildRepository;
    private final SizeRecommender sizeRecommender;

    public SizeRecommendationReader(
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendReplicaChildRepository replicaChildRepository,
        GrowthChart growthChart) {
        this.replicaProductRepository = replicaProductRepository;
        this.replicaChildRepository = replicaChildRepository;
        this.sizeRecommender = new SizeRecommender(growthChart);
    }

    // 상품의 사이즈 줄은 지연 로딩이므로 트랜잭션 안에서 읽어 계산한다
    @Transactional(readOnly = true)
    public SizeRecommendationDraft read(Long memberId, Long productId, Long childId) {
        RecommendReplicaProduct product = replicaProductRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.PRODUCT_NOT_FOUND));
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(SERVICE_ZONE);
        ChildSizeInput childInput = child.toChildSizeInput();
        ProductSizes productSizes = product.toProductSizes();
        SizeRecommendation rule = sizeRecommender.recommend(childInput, productSizes, today);
        int ageMonths = (int) Period.between(child.getBirthDate(), today).toTotalMonths();
        return new SizeRecommendationDraft(
            productId, childId, productSizes, rule,
            keyInputOf(productId, childId, child, ageMonths),
            questionOf(product, productSizes, child, childInput, rule, ageMonths, today));
    }

    private AiResponseKey.Input keyInputOf(Long productId, Long childId, RecommendReplicaChild child, int ageMonths) {
        return new AiResponseKey.Input(
            productId, childId, ageMonths, child.getHeightCm(), child.getWeightKg(),
            child.getBodyType(), child.getPreferredFit(), null, null, PROMPT_VERSION);
    }

    private SizeAiQuestion questionOf(
        RecommendReplicaProduct product, ProductSizes productSizes, RecommendReplicaChild child,
        ChildSizeInput childInput, SizeRecommendation rule, int ageMonths, LocalDate today) {
        SizeAiQuestion.Child questionChild = new SizeAiQuestion.Child(
            ageMonths, childInput.heightCm(), childInput.weightKg(), child.getBodyType(),
            child.getPreferredFit(), sizeRecommender.decidedBodyType(childInput, today));
        return new SizeAiQuestion(questionChild, sizeLinesOf(product, productSizes), ruleResultOf(rule), null, null);
    }

    private List<SizeAiQuestion.SizeLine> sizeLinesOf(RecommendReplicaProduct product, ProductSizes productSizes) {
        List<SizeAiQuestion.SizeLine> lines = new ArrayList<>();
        for (RecommendReplicaSizeSpec spec : product.getSizeSpecs()) {
            if (productSizes.hasSize(spec.getSize())) {
                lines.add(new SizeAiQuestion.SizeLine(
                    spec.getSize(), spec.getMinHeightCm(), spec.getMaxHeightCm(), spec.getMeasurements()));
            }
        }
        return lines;
    }

    private SizeAiQuestion.RuleResult ruleResultOf(SizeRecommendation rule) {
        String fit = rule.getFit() == null ? null : rule.getFit().name();
        List<String> notices = new ArrayList<>();
        for (SizeNotice notice : rule.getNotices()) {
            notices.add(notice.name());
        }
        return new SizeAiQuestion.RuleResult(rule.getRecommendedSize(), fit, rule.getCandidateSizes(), notices);
    }
}
