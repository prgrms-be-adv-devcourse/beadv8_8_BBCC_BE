package com.bbcc.kidly.boundedcontext.recommend.app.size.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.FitProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaCategoryPathEntry;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSizeSpec;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeAiEvidence;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이즈 추천에 필요한 복사본을 트랜잭션 안에서 읽고, 규칙 계산과 AI 입력까지 만들어 초안으로 돌려준다.
 * AI 호출은 트랜잭션 밖에서 해야 해서 서비스가 아니라 이 클래스가 읽기를 맡는다.
 */
@Component
public class SizeRecommendationReader {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PROMPT_VERSION = 3;
    private static final int GROWTH_WINDOW_MONTHS = 12;
    private static final int CM_SCALE = 2;
    private static final int PERCENT = 100;

    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendReplicaChildRepository replicaChildRepository;
    private final ReferenceGarmentRepository referenceGarmentRepository;
    private final RecommendGrowthRecordRepository growthRecordRepository;
    private final SizeRecommender sizeRecommender;
    private final GrowthVelocityCalculator growthVelocityCalculator;

    public SizeRecommendationReader(
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendReplicaChildRepository replicaChildRepository,
        ReferenceGarmentRepository referenceGarmentRepository,
        RecommendGrowthRecordRepository growthRecordRepository,
        GrowthChart growthChart,
        GrowthVelocityTable growthVelocityTable) {
        this.replicaProductRepository = replicaProductRepository;
        this.replicaChildRepository = replicaChildRepository;
        this.referenceGarmentRepository = referenceGarmentRepository;
        this.growthRecordRepository = growthRecordRepository;
        this.sizeRecommender = new SizeRecommender(growthChart);
        this.growthVelocityCalculator = new GrowthVelocityCalculator(growthVelocityTable);
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
        List<SizeAiQuestion.SizeLine> sizeLines = sizeLinesOf(product, productSizes);
        ReferenceFit referenceFit = referenceFitOf(product, productSizes, sizeLines, childId);
        SizeAiQuestion.GrowthSpeed growthSpeed = growthSpeedOf(child, today);
        SizeAiQuestion question = questionOf(
            sizeLines, referenceFit, growthSpeed, child, childInput, rule, ageMonths, today);
        return new SizeRecommendationDraft(
            productId, childId, productSizes, rule,
            keyInputOf(productId, childId, child, ageMonths, referenceFit), question,
            new SizeAiEvidence(referenceFit, givenNumbersOf(question)));
    }

    // 질문을 만든 뒤 그 질문에서 모아서, AI에게 준 숫자와 답 검증에 쓰는 숫자가 어긋나지 않게 한다.
    private Set<BigDecimal> givenNumbersOf(SizeAiQuestion question) {
        Set<BigDecimal> numbers = new HashSet<>();
        numbers.add(BigDecimal.valueOf(question.child().ageMonths()));
        numbers.add(BigDecimal.valueOf(question.child().heightCm()));
        numbers.add(BigDecimal.valueOf(question.child().weightKg()));
        for (SizeAiQuestion.SizeLine line : question.sizes()) {
            addIfNumber(numbers, line.size());
            addIfNumber(numbers, line.minHeightCm());
            addIfNumber(numbers, line.maxHeightCm());
            numbers.addAll(line.measurements().values());
        }
        addIfNumber(numbers, question.ruleResult().recommendedSize());
        for (String candidateSize : question.ruleResult().candidateSizes()) {
            addIfNumber(numbers, candidateSize);
        }
        addReferenceFitNumbers(numbers, question.referenceFit());
        return numbers;
    }

    private void addReferenceFitNumbers(Set<BigDecimal> numbers, ReferenceFit referenceFit) {
        if (referenceFit == null) {
            return;
        }
        for (ReferenceFit.Item item : referenceFit.items()) {
            numbers.add(item.garmentCm());
            for (ReferenceFit.SizeDiff diff : item.sizes()) {
                numbers.add(diff.productCm());
                numbers.add(diff.diffCm());
            }
        }
    }

    private void addIfNumber(Set<BigDecimal> numbers, Integer value) {
        if (value != null) {
            numbers.add(BigDecimal.valueOf(value));
        }
    }

    // 「110」이나 「130-150mm」처럼 사이즈 이름에 든 숫자를 담고, 「FREE」나 「M」은 숫자가 없어 건너뛴다. null도 건너뛴다.
    private void addIfNumber(Set<BigDecimal> numbers, String sizeName) {
        if (sizeName != null) {
            numbers.addAll(SizeAiEvidence.numbersIn(sizeName));
        }
    }

    // 기준 옷이 없거나 상품과 비교할 수 없으면 null이다. 사이즈 무관 상품은 비교하지 않으므로 기준 옷을 읽지 않는다.
    // 비교는 AI에 보내는 사이즈 줄과 같은 줄로 한다.
    private ReferenceFit referenceFitOf(RecommendReplicaProduct product, ProductSizes productSizes,
        List<SizeAiQuestion.SizeLine> sizeLines, Long childId) {
        if (productSizes.isSizeFree()) {
            return null;
        }
        List<String> categoryPathNames = new ArrayList<>();
        for (RecommendReplicaCategoryPathEntry entry : product.getCategoryPath()) {
            categoryPathNames.add(entry.getCategoryName());
        }
        List<FitProduct.SizeMeasurements> sizes = new ArrayList<>();
        for (SizeAiQuestion.SizeLine line : sizeLines) {
            sizes.add(new FitProduct.SizeMeasurements(line.size(), line.measurements()));
        }
        FitProduct fitProduct = new FitProduct(
            product.getCategoryId(), categoryPathNames, productSizes.isSizeFree(), sizes);
        return ReferenceFit.compare(fitProduct, referenceGarmentRepository.findAllByChildProfileId(childId))
            .orElse(null);
    }

    private AiResponseKey.Input keyInputOf(Long productId, Long childId, RecommendReplicaChild child, int ageMonths,
        ReferenceFit referenceFit) {
        return new AiResponseKey.Input(
            productId, childId, ageMonths, child.getHeightCm(), child.getWeightKg(),
            child.getBodyType(), child.getPreferredFit(), null, null, PROMPT_VERSION,
            referenceFit == null ? null : referenceFit.keyText());
    }

    private SizeAiQuestion questionOf(
        List<SizeAiQuestion.SizeLine> sizeLines, ReferenceFit referenceFit, SizeAiQuestion.GrowthSpeed growthSpeed,
        RecommendReplicaChild child, ChildSizeInput childInput, SizeRecommendation rule, int ageMonths,
        LocalDate today) {
        SizeAiQuestion.Child questionChild = new SizeAiQuestion.Child(
            ageMonths, childInput.heightCm(), childInput.weightKg(), child.getBodyType(),
            child.getPreferredFit(), sizeRecommender.decidedBodyType(childInput, today));
        return new SizeAiQuestion(questionChild, sizeLines, ruleResultOf(rule), referenceFit, growthSpeed);
    }

    // 성장 속도 계산이 보는 기간(12개월) 안의 이 자녀 기록만 읽는다
    private SizeAiQuestion.GrowthSpeed growthSpeedOf(RecommendReplicaChild child, LocalDate today) {
        LocalDateTime since = today.minusMonths(GROWTH_WINDOW_MONTHS).atStartOfDay();
        List<RecommendGrowthRecord> records =
            growthRecordRepository.findRecordedSince(child.getChildProfileId(), since);
        GrowthVelocity velocity =
            growthVelocityCalculator.calculate(child.getGender(), child.getBirthDate(), records, today);
        return new SizeAiQuestion.GrowthSpeed(
            rounded(velocity.cmPerMonth()), roundedOrNull(velocity.recentCmPerMonth()), velocity.recordCount(),
            rounded(velocity.recordMonths()), (int) Math.round(velocity.childShare() * PERCENT),
            rounded(velocity.tableCmPerMonth()), velocity.tableSource());
    }

    private BigDecimal rounded(double value) {
        return BigDecimal.valueOf(value).setScale(CM_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal roundedOrNull(Double value) {
        if (value == null) {
            return null;
        }
        return rounded(value);
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
