package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceFit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI에게 준 질문에서 AI 답을 검증할 때 쓰는 값이다.
 * 기준 핏이 없으면 referenceFit은 null이다. givenNumbers는 질문에 실제로 담긴 숫자들이고,
 * 2와 2.0이 같은 값이 되도록 끝의 0을 뗀 값으로 보관한다.
 */
public record SizeAiEvidence(ReferenceFit referenceFit, Set<BigDecimal> givenNumbers) {

    // 정수나 소수만 숫자로 본다. 「-」는 부호로 보지 않아서 「0 ~ 3cm」나 「110-120」에서도 숫자만 뽑힌다.
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    public SizeAiEvidence {
        requireNonNull(givenNumbers, "givenNumbers는 필수입니다");
        Set<BigDecimal> normalized = new HashSet<>();
        for (BigDecimal number : givenNumbers) {
            normalized.add(normalize(number));
        }
        givenNumbers = Set.copyOf(normalized);
    }

    /**
     * 글에서 숫자를 모두 뽑는다. 사이즈 이름이나 근거 문장처럼 숫자가 섞인 글에서 숫자를 찾을 때 쓴다.
     */
    public static List<BigDecimal> numbersIn(String text) {
        List<BigDecimal> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            numbers.add(new BigDecimal(matcher.group()));
        }
        return numbers;
    }

    static BigDecimal normalize(BigDecimal number) {
        return number.stripTrailingZeros();
    }

    /**
     * 글에서 뽑은 숫자는 부호가 없으므로, 준 숫자가 그 값이거나 그 값의 음수이면 준 숫자로 본다.
     */
    boolean isGiven(BigDecimal number) {
        BigDecimal normalized = normalize(number);
        return givenNumbers.contains(normalized) || givenNumbers.contains(normalized.negate());
    }
}
