package com.bbcc.kidly.boundedcontext.recommend.in.size;

import com.bbcc.kidly.boundedcontext.recommend.app.size.SizeRecommendationService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationRequest;
import com.bbcc.kidly.boundedcontext.recommend.in.size.dto.SizeRecommendationResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 상세에서 로그인한 회원의 자녀에게 맞는 사이즈를 추천하는 API다.
 */
@RestController
@RequiredArgsConstructor
public class SizeRecommendationController {

    private final SizeRecommendationService sizeRecommendationService;

    @GetMapping("/api/v1/products/{productId}/size-recommendation")
    public SizeRecommendationResponse recommend(
        @LoginMember AuthMember member,
        @PathVariable Long productId,
        @Valid @ModelAttribute SizeRecommendationRequest request) {
        return SizeRecommendationResponse.from(
            sizeRecommendationService.recommend(member.id(), productId, request.childId()));
    }
}
