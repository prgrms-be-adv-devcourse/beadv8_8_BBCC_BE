package com.bbcc.kidly.boundedcontext.recommend.app.port.ai;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.List;

/**
 * 홈 맛보기 상품을 고르려고 외부 AI에 보내는 요청이다.
 * 외부로 나가는 값이므로 자녀를 알아볼 수 있는 이름 · 생년월일과 회원 ID · 자녀 ID는 담지 않는다.
 *
 * @param child 자녀의 몸 정보다
 * @param candidates AI가 고를 후보 상품이다. 선호 점수 순서이다
 */
public record HomePreviewAiRequest(Child child, List<Candidate> candidates) {

    /**
     * 자녀의 몸 정보다. 값이 없는 선택 항목은 null이다.
     */
    public record Child(
        Integer ageMonths,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BodyType bodyType,
        PreferredFit preferredFit) {
    }

    /**
     * 후보 상품 하나다.
     *
     * @param categoryPath 대분류부터 종류까지의 카테고리 이름이다
     * @param recommendedSize 사이즈 무관 상품이면 null이다
     * @param evidence 자녀 활동으로 만든 근거 라벨이다. 맞는 근거가 없으면 빈 목록이다
     * @param colors 판매 중인 옵션의 색상이다
     * @param productInfo 상품 정보 값(소재 등)과 설명을 한 줄로 이은 글이다. 없으면 null이다
     */
    public record Candidate(
        Long productId,
        String name,
        String brand,
        List<String> categoryPath,
        Long minPrice,
        String recommendedSize,
        List<String> evidence,
        List<String> colors,
        String productInfo) {

        public Candidate {
            evidence = List.copyOf(evidence);
            colors = List.copyOf(colors);
        }

        /**
         * 근거 · 색상 · 상품 정보 없이 후보를 만든다.
         */
        public Candidate(Long productId, String name, String brand, List<String> categoryPath, Long minPrice,
            String recommendedSize) {
            this(productId, name, brand, categoryPath, minPrice, recommendedSize, List.of(), List.of(), null);
        }
    }
}
