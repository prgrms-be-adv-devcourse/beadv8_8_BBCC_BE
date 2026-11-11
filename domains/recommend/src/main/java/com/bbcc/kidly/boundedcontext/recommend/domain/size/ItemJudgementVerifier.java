package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI가 적은 항목별 판단을, AI에게 넘긴 기준 핏과 맞는지 검사하면서 {@link ItemJudgement}로 바꾼다.
 * 하나라도 맞지 않으면 그 답의 판단은 믿을 수 없으므로 목록 전체를 버리고 비어 있는 Optional을 돌려준다.
 */
final class ItemJudgementVerifier {

    private ItemJudgementVerifier() {
    }

    /**
     * @param size AI가 추천한 사이즈다. 판단의 차이는 이 사이즈의 차이와 같아야 한다.
     */
    static Optional<List<ItemJudgement>> verify(List<AiSizeAnswer.Judgement> aiJudgements, String size,
        SizeAiEvidence evidence) {
        List<ItemJudgement> judgements = new ArrayList<>();
        for (AiSizeAnswer.Judgement aiJudgement : aiJudgements) {
            ItemJudgement judgement = toVerified(aiJudgement, size, evidence.referenceFit());
            if (judgement == null) {
                return Optional.empty();
            }
            judgements.add(judgement);
        }
        return Optional.of(List.copyOf(judgements));
    }

    private static ItemJudgement toVerified(AiSizeAnswer.Judgement aiJudgement, String size,
        ReferenceFit referenceFit) {
        if (aiJudgement == null || referenceFit == null || !hasValidAllowance(aiJudgement)) {
            return null;
        }
        Fit fit = Fit.findByName(aiJudgement.fit());
        BigDecimal givenDiff = givenDiff(referenceFit, aiJudgement.code(), size);
        if (fit == null || givenDiff == null || aiJudgement.diffCm() == null
            || aiJudgement.diffCm().compareTo(givenDiff) != 0) {
            return null;
        }
        return new ItemJudgement(aiJudgement.code(), aiJudgement.diffCm(), fit,
            aiJudgement.allowanceMinCm(), aiJudgement.allowanceMaxCm());
    }

    // 여유 범위는 최소가 최대보다 클 수 없다.
    private static boolean hasValidAllowance(AiSizeAnswer.Judgement aiJudgement) {
        BigDecimal min = aiJudgement.allowanceMinCm();
        BigDecimal max = aiJudgement.allowanceMaxCm();
        return min != null && max != null && min.compareTo(max) <= 0;
    }

    // 기준 핏에 그 항목이나 그 사이즈가 없으면 null이다.
    private static BigDecimal givenDiff(ReferenceFit referenceFit, String code, String size) {
        for (ReferenceFit.Item item : referenceFit.items()) {
            if (!item.code().equals(code)) {
                continue;
            }
            for (ReferenceFit.SizeDiff diff : item.sizes()) {
                if (diff.size().equals(size)) {
                    return diff.diffCm();
                }
            }
        }
        return null;
    }
}
