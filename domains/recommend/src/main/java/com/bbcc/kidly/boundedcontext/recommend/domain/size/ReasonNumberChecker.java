package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI가 쓴 근거 문장의 숫자가 모두 AI에게 준 숫자인지 검사한다. AI가 지어낸 숫자가 화면에 나가지 않게 하기 위해서다.
 * 문장의 숫자는 부호 없이 뽑으므로 준 숫자는 부호를 뺀 값도 허용한다. 판단의 여유 범위도 AI가 문장에 쓸 수 있는 값으로 본다.
 */
final class ReasonNumberChecker {

    private ReasonNumberChecker() {
    }

    static boolean allNumbersGiven(List<SizeReason> reasons, SizeAiEvidence evidence,
        List<ItemJudgement> judgements) {
        for (SizeReason reason : reasons) {
            for (BigDecimal number : SizeAiEvidence.numbersIn(reason.detail())) {
                if (!evidence.isGiven(number) && !isAllowance(number, judgements)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAllowance(BigDecimal number, List<ItemJudgement> judgements) {
        BigDecimal normalized = SizeAiEvidence.normalize(number);
        for (ItemJudgement judgement : judgements) {
            if (normalized.equals(SizeAiEvidence.normalize(judgement.allowanceMinCm()))
                || normalized.equals(SizeAiEvidence.normalize(judgement.allowanceMaxCm()))) {
                return true;
            }
        }
        return false;
    }
}
