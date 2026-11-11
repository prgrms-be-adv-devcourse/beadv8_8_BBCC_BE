package com.bbcc.kidly.boundedcontext.recommend.app.size;

import com.bbcc.kidly.boundedcontext.recommend.app.ai.AiResponseService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.AiSizeChooser;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationResult;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationDraft;
import com.bbcc.kidly.boundedcontext.recommend.app.size.support.SizeRecommendationReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ItemJudgement;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeNotice;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeReason;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendation;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommendationSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사이즈 추천 유스케이스다. AI 호출은 트랜잭션 밖에서 해야 해서 이 클래스에는 {@code @Transactional}을 붙이지 않고,
 * 복사본 읽기는 {@link SizeRecommendationReader}가 트랜잭션 안에서 맡는다.
 */
@Slf4j
@Service
public class SizeRecommendationService {

    private final SizeRecommendationReader reader;
    private final AiResponseService aiResponseService;
    private final Optional<AiSizeChooser> aiSizeChooser;

    public SizeRecommendationService(
        SizeRecommendationReader reader,
        AiResponseService aiResponseService,
        Optional<AiSizeChooser> aiSizeChooser) {
        this.reader = reader;
        this.aiResponseService = aiResponseService;
        this.aiSizeChooser = aiSizeChooser;
    }

    public SizeRecommendationResult recommend(Long memberId, Long productId, Long childId) {
        SizeRecommendationDraft draft = reader.read(memberId, productId, childId);
        AiSizeAnswer answer = askAi(draft);
        SizeRecommendation recommendation = draft.rule().decideWith(draft.productSizes(), answer, draft.aiEvidence());
        logIfAdopted(draft, recommendation);
        return toResult(draft, recommendation);
    }

    // AI 모델이 없거나 사이즈 구분이 없는 상품이면 부르지 않고, 답을 얻지 못하면 null이라 규칙 결과로 답한다
    private AiSizeAnswer askAi(SizeRecommendationDraft draft) {
        if (aiSizeChooser.isEmpty() || draft.productSizes().isSizeFree()) {
            return null;
        }
        AiSizeChooser chooser = aiSizeChooser.get();
        return aiResponseService.respond(
            AiResponseType.SIZE_RECOMMENDATION, draft.aiKeyInput(), AiSizeAnswer.class,
            () -> chooser.choose(draft.aiQuestion())).orElse(null);
    }

    // 어떤 프롬프트 판에서 어떤 판단을 채택했는지 남긴다. 자녀 ID, 회원 ID, 자녀 몸 정보는 남기지 않는다.
    private void logIfAdopted(SizeRecommendationDraft draft, SizeRecommendation recommendation) {
        if (recommendation.getSource() != SizeRecommendationSource.AI) {
            return;
        }
        List<String> judgements = new ArrayList<>();
        for (ItemJudgement judgement : recommendation.getItemJudgements()) {
            judgements.add(judgement.code() + " " + judgement.diffCm() + " " + judgement.fit() + " "
                + judgement.allowanceMinCm() + "~" + judgement.allowanceMaxCm());
        }
        log.info("사이즈 AI 답을 채택했습니다. promptVersion={}, productId={}, itemJudgements={}",
            draft.aiKeyInput().promptVersion(), draft.productId(), judgements);
    }

    private SizeRecommendationResult toResult(SizeRecommendationDraft draft, SizeRecommendation recommendation) {
        String fit = recommendation.getFit() == null ? null : recommendation.getFit().name();
        List<SizeRecommendationResult.Notice> notices = new ArrayList<>();
        for (SizeNotice notice : recommendation.getNotices()) {
            notices.add(new SizeRecommendationResult.Notice(notice.name(), notice.getMessage()));
        }
        List<SizeRecommendationResult.Reason> reasons = new ArrayList<>();
        for (SizeReason reason : recommendation.getReasons()) {
            reasons.add(new SizeRecommendationResult.Reason(
                reason.type().name(), reason.detail(), reason.sourceId()));
        }
        // 지난 구매 사이즈는 아직 채우지 않는다
        return new SizeRecommendationResult(
            draft.productId(), draft.childId(), recommendation.getRecommendedSize(), fit,
            notices, recommendation.getCandidateSizes(), null,
            recommendation.getSource().name(), reasons,
            referenceFitOf(draft.aiEvidence().referenceFit()), itemJudgementsOf(recommendation));
    }

    private SizeRecommendationResult.ReferenceFit referenceFitOf(ReferenceFit referenceFit) {
        if (referenceFit == null) {
            return null;
        }
        List<SizeRecommendationResult.FitItem> items = new ArrayList<>();
        for (ReferenceFit.Item item : referenceFit.items()) {
            List<SizeRecommendationResult.SizeDiff> sizes = new ArrayList<>();
            for (ReferenceFit.SizeDiff diff : item.sizes()) {
                sizes.add(new SizeRecommendationResult.SizeDiff(diff.size(), diff.productCm(), diff.diffCm()));
            }
            items.add(new SizeRecommendationResult.FitItem(item.code(), item.garmentCm(), sizes));
        }
        return new SizeRecommendationResult.ReferenceFit(
            referenceFit.productPart().name(), referenceFit.comparedPart().name(), items);
    }

    private List<SizeRecommendationResult.ItemJudgement> itemJudgementsOf(SizeRecommendation recommendation) {
        List<SizeRecommendationResult.ItemJudgement> judgements = new ArrayList<>();
        for (ItemJudgement judgement : recommendation.getItemJudgements()) {
            judgements.add(new SizeRecommendationResult.ItemJudgement(judgement.code(), judgement.diffCm(),
                judgement.fit().name(), judgement.allowanceMinCm(), judgement.allowanceMaxCm()));
        }
        return judgements;
    }
}
