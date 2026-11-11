package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI가 쓴 근거 문장의 숫자가 모두 AI에게 준 숫자인지 검사한다. AI가 지어낸 숫자가 화면에 나가지 않게 하기 위해서다.
 * 문장의 숫자는 정수나 소수만 뽑고 「-」는 부호로 보지 않는다. 「0 ~ 3cm」나 「110-120」처럼 범위를 쓸 수 있기 때문이다.
 * 그래서 준 숫자는 부호를 뺀 값도 허용한다. 판단의 여유 범위도 AI가 문장에 쓸 수 있는 값으로 본다.
 */
final class ReasonNumberChecker {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private ReasonNumberChecker() {
    }

    static boolean allNumbersGiven(List<SizeReason> reasons, Set<BigDecimal> givenNumbers,
        List<ItemJudgement> judgements) {
        for (SizeReason reason : reasons) {
            Matcher matcher = NUMBER.matcher(reason.detail());
            while (matcher.find()) {
                if (!isGiven(new BigDecimal(matcher.group()), givenNumbers, judgements)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isGiven(BigDecimal number, Set<BigDecimal> givenNumbers, List<ItemJudgement> judgements) {
        for (BigDecimal given : givenNumbers) {
            if (number.compareTo(given) == 0 || number.compareTo(given.abs()) == 0) {
                return true;
            }
        }
        for (ItemJudgement judgement : judgements) {
            if (number.compareTo(judgement.allowanceMinCm()) == 0
                || number.compareTo(judgement.allowanceMaxCm()) == 0) {
                return true;
            }
        }
        return false;
    }
}
