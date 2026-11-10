package com.bbcc.kidly.boundedcontext.recommend.domain.ai;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.List;

/**
 * 지난 AI 답을 다시 쓰기 위한 키다. 답에 영향을 주는 입력만 해시에 넣고, 회원 ID, 자녀 이름, 요청 시각, 장바구니 수량은 넣지 않는다.
 */
public record AiResponseKey(String value) {

    public static AiResponseKey of(Input input) {
        throw new UnsupportedOperationException();
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
