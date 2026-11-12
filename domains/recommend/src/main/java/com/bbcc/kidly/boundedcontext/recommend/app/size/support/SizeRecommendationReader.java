package com.bbcc.kidly.boundedcontext.recommend.app.size.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GirthRatioTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocity;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.GrowthVelocityTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.RecommendGrowthRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriod;
import com.bbcc.kidly.boundedcontext.recommend.domain.growth.WearablePeriodCalculator;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseHistory;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecordRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchasedGarment;
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
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.BodyHeightRatioTable;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreview;
import com.bbcc.kidly.boundedcontext.recommend.domain.wearpreview.WearPreviewCalculator;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이즈 추천에 필요한 복사본을 트랜잭션 안에서 읽고, 규칙 계산과 AI 입력까지 만들어 초안으로 돌려준다.
 * AI 호출은 트랜잭션 밖에서 해야 해서 서비스가 아니라 이 클래스가 읽기를 맡는다.
 */
@Component
public class SizeRecommendationReader {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PROMPT_VERSION = 5;

    private final RecommendReplicaProductRepository replicaProductRepository;
    private final RecommendReplicaChildRepository replicaChildRepository;
    private final ReferenceGarmentRepository referenceGarmentRepository;
    private final RecommendGrowthRecordRepository growthRecordRepository;
    private final PurchaseRecordRepository purchaseRecordRepository;
    private final SizeRecommender sizeRecommender;
    private final GrowthVelocityCalculator growthVelocityCalculator;
    private final WearablePeriodCalculator wearablePeriodCalculator;
    private final WearPreviewCalculator wearPreviewCalculator;

    public SizeRecommendationReader(
        RecommendReplicaProductRepository replicaProductRepository,
        RecommendReplicaChildRepository replicaChildRepository,
        ReferenceGarmentRepository referenceGarmentRepository,
        RecommendGrowthRecordRepository growthRecordRepository,
        PurchaseRecordRepository purchaseRecordRepository,
        GrowthChart growthChart,
        GrowthVelocityTable growthVelocityTable,
        GirthRatioTable girthRatioTable,
        BodyHeightRatioTable bodyHeightRatioTable) {
        this.replicaProductRepository = replicaProductRepository;
        this.replicaChildRepository = replicaChildRepository;
        this.referenceGarmentRepository = referenceGarmentRepository;
        this.growthRecordRepository = growthRecordRepository;
        this.purchaseRecordRepository = purchaseRecordRepository;
        this.sizeRecommender = new SizeRecommender(growthChart);
        this.growthVelocityCalculator = new GrowthVelocityCalculator(growthVelocityTable);
        this.wearablePeriodCalculator = new WearablePeriodCalculator(girthRatioTable);
        this.wearPreviewCalculator = new WearPreviewCalculator(bodyHeightRatioTable);
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
        List<String> categoryPathNames = categoryPathNamesOf(product);
        // 기준 핏 비교와 착용 미리보기가 같은 상품 입력을 쓴다
        FitProduct fitProduct = fitProductOf(product, categoryPathNames, productSizes, sizeLines);
        ReferenceFit referenceFit = referenceFitOf(fitProduct, childId);
        WearPreview wearPreview = wearPreviewCalculator.preview(fitProduct, childInput, today).orElse(null);
        GrowthVelocity velocity = growthVelocityOf(child, today);
        List<WearablePeriod> wearablePeriods = wearablePeriodCalculator.calculate(
            new WearablePeriodCalculator.WearInfo(
                productSizes, categoryPathNames, childInput, today, velocity, referenceFit));
        SizeAiQuestion.GrowthSpeed growthSpeed = SizeAiQuestion.GrowthSpeed.from(velocity);
        PurchaseHistory purchaseHistory = purchaseHistoryOf(
            product, fitProduct, childId, growthSpeed.cmPerMonth(), today);
        SizeAiQuestion question = new SizeAiQuestion(
            questionChildOf(child, childInput, ageMonths, today), sizeLines, ruleResultOf(rule), referenceFit,
            growthSpeed, wearablePeriods, purchaseHistory.purchases());
        return new SizeRecommendationDraft(
            productId, childId, productSizes, rule,
            keyInputOf(productId, childId, child, ageMonths, question), question,
            new SizeAiEvidence(referenceFit, question.givenNumbers()), wearPreview,
            purchaseHistory.lastPurchasedSize());
    }

    // 보는 기간 안의 이 자녀 구매 기록을 읽고, 구매마다 산 상품 복사본에서 칸 · 브랜드 · 산 사이즈의 실측을 찾아 지금 상품과 견준다
    private PurchaseHistory purchaseHistoryOf(RecommendReplicaProduct product, FitProduct fitProduct, Long childId,
        BigDecimal cmPerMonth, LocalDate today) {
        LocalDateTime since = today.minusMonths(PurchaseHistory.WINDOW_MONTHS).atStartOfDay();
        List<PurchaseRecord> records = purchaseRecordRepository.findConfirmedSince(childId, since);
        List<Long> productIds = new ArrayList<>();
        for (PurchaseRecord record : records) {
            productIds.add(record.getProductId());
        }
        Map<Long, RecommendReplicaProduct> boughtProducts = new HashMap<>();
        for (RecommendReplicaProduct bought : replicaProductRepository.findAllById(productIds)) {
            boughtProducts.put(bought.getProductId(), bought);
        }
        List<PurchasedGarment> garments = new ArrayList<>();
        for (PurchaseRecord record : records) {
            garments.add(purchasedGarmentOf(record, boughtProducts.get(record.getProductId())));
        }
        // 성장 속도는 AI에 준 반올림 값을 쓴다. 그래야 예상 성장량이 질문에 있는 숫자와 맞는다.
        return PurchaseHistory.compare(new PurchaseHistory.CompareInfo(
            fitProduct, product.getBrand(), garments, cmPerMonth, today));
    }

    // 산 상품 복사본이 없으면 칸 · 브랜드 · 실측을 몰라 비워 넘기고, 칸을 모르는 구매는 domain이 뺀다.
    private PurchasedGarment purchasedGarmentOf(PurchaseRecord record, RecommendReplicaProduct bought) {
        if (bought == null) {
            return new PurchasedGarment(record.getId(), record.getSize(), record.getConfirmedAt(), null, List.of(),
                null, Map.of());
        }
        return new PurchasedGarment(record.getId(), record.getSize(), record.getConfirmedAt(), bought.getCategoryId(),
            categoryPathNamesOf(bought), bought.getBrand(), measurementsOf(bought, record.getSize()));
    }

    // 산 사이즈의 실측이고, 그 사이즈 줄이 없으면 빈 Map이다
    private Map<String, BigDecimal> measurementsOf(RecommendReplicaProduct bought, String size) {
        for (RecommendReplicaSizeSpec spec : bought.getSizeSpecs()) {
            if (spec.getSize().equals(size)) {
                return spec.getMeasurements();
            }
        }
        return Map.of();
    }

    // AI에 보내는 사이즈 줄과 같은 실측으로 만든 상품 입력이다
    private FitProduct fitProductOf(RecommendReplicaProduct product, List<String> categoryPathNames,
        ProductSizes productSizes, List<SizeAiQuestion.SizeLine> sizeLines) {
        List<FitProduct.SizeMeasurements> sizes = new ArrayList<>();
        for (SizeAiQuestion.SizeLine line : sizeLines) {
            sizes.add(new FitProduct.SizeMeasurements(line.size(), line.measurements()));
        }
        return new FitProduct(product.getCategoryId(), categoryPathNames, productSizes.isSizeFree(), sizes);
    }

    // 기준 옷이 없거나 상품과 비교할 수 없으면 null이다. 사이즈 무관 상품은 비교하지 않으므로 기준 옷을 읽지 않는다.
    private ReferenceFit referenceFitOf(FitProduct fitProduct, Long childId) {
        if (fitProduct.sizeFree()) {
            return null;
        }
        return ReferenceFit.compare(fitProduct, referenceGarmentRepository.findAllByChildProfileId(childId))
            .orElse(null);
    }

    private List<String> categoryPathNamesOf(RecommendReplicaProduct product) {
        List<String> names = new ArrayList<>();
        for (RecommendReplicaCategoryPathEntry entry : product.getCategoryPath()) {
            names.add(entry.getCategoryName());
        }
        return names;
    }

    private AiResponseKey.Input keyInputOf(Long productId, Long childId, RecommendReplicaChild child, int ageMonths,
        SizeAiQuestion question) {
        return new AiResponseKey.Input(
            productId, childId, ageMonths, child.getHeightCm(), child.getWeightKg(),
            child.getBodyType(), child.getPreferredFit(), null, null, PROMPT_VERSION, question.keyText());
    }

    private SizeAiQuestion.Child questionChildOf(
        RecommendReplicaChild child, ChildSizeInput childInput, int ageMonths, LocalDate today) {
        return new SizeAiQuestion.Child(
            ageMonths, childInput.heightCm(), childInput.weightKg(), child.getBodyType(),
            child.getPreferredFit(), sizeRecommender.decidedBodyType(childInput, today));
    }

    // 성장 속도 계산이 보는 기간 안의 이 자녀 기록만 읽는다
    private GrowthVelocity growthVelocityOf(RecommendReplicaChild child, LocalDate today) {
        LocalDateTime since = today.minusMonths(GrowthVelocityCalculator.WINDOW_MONTHS).atStartOfDay();
        List<RecommendGrowthRecord> records =
            growthRecordRepository.findRecordedSince(child.getChildProfileId(), since);
        return growthVelocityCalculator.calculate(child.getGender(), child.getBirthDate(), records, today);
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
