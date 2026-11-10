package com.bbcc.kidly.boundedcontext.recommend.domain.ai;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * 지난 AI 답을 다시 쓰기 위한 키다. 답에 영향을 주는 입력만 해시에 넣고, 회원 ID, 자녀 이름, 요청 시각, 장바구니 수량은 넣지 않는다.
 */
public record AiResponseKey(String value) {

    public static AiResponseKey of(Input input) {
        String text = "productId=" + input.productId()
            + "\nchildProfileId=" + input.childProfileId()
            + "\nageMonths=" + input.ageMonths()
            + "\nheightCm=" + decimalText(input.heightCm())
            + "\nweightKg=" + decimalText(input.weightKg())
            + "\nbodyType=" + input.bodyType().name()
            + "\npreferredFit=" + input.preferredFit().name()
            + "\ncandidateProductIds=" + joinIds(input.candidateProductIds())
            + "\ncartProductIds=" + joinIds(input.cartProductIds())
            + "\npromptVersion=" + input.promptVersion();
        return new AiResponseKey(sha256Hex(text));
    }

    private static String decimalText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String joinIds(List<Long> ids) {
        List<Long> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        StringBuilder joined = new StringBuilder();
        for (Long id : sorted) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(id);
        }
        return joined.toString();
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK는 SHA-256을 항상 제공합니다", e);
        }
    }

    public record Input(
        Long productId,
        Long childProfileId,
        Integer ageMonths,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BodyType bodyType,
        PreferredFit preferredFit,
        List<Long> candidateProductIds,
        List<Long> cartProductIds,
        int promptVersion
    ) {
    }
}
