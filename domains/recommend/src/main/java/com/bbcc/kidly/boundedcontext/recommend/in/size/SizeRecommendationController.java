package com.bbcc.kidly.boundedcontext.recommend.in.size;

import com.bbcc.kidly.boundedcontext.recommend.app.size.SizeRecommendationService;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.size.dto.SizeRecommendationWithAi;
import com.bbcc.kidly.boundedcontext.recommend.in.size.dto.SizeRecommendationResponse;
import com.bbcc.kidly.boundedcontext.recommend.in.support.AiResultStream;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 상품 상세에서 로그인한 회원의 자녀에게 맞는 사이즈를 추천하는 API다.
 */
@RestController
@RequiredArgsConstructor
public class SizeRecommendationController {

    private static final String PATH = "/api/v1/recommendations/size";

    private final SizeRecommendationService sizeRecommendationService;
    private final AiResultStream aiResultStream;

    @GetMapping(PATH)
    public SizeRecommendationResponse recommend(
        @LoginMember AuthMember member,
        @Valid @ModelAttribute SizeRecommendationRequest request) {
        return SizeRecommendationResponse.from(
            sizeRecommendationService.recommend(member.id(), request.productId(), request.childId()));
    }

    /**
     * 같은 주소를 스트림(text/event-stream)으로 부르면 AI 답을 기다리지 않는다. 규칙 결과를 rule 이벤트로 먼저 보내고,
     * AI 답이 오면 ai 이벤트를 보낸 뒤 닫는다. 없는 상품 · 다른 회원의 자녀 같은 오류는 스트림을 열기 전에 ProblemDetail로 낸다.
     */
    @GetMapping(value = PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
        @LoginMember AuthMember member,
        @Valid @ModelAttribute SizeRecommendationRequest request,
        HttpServletResponse response) {
        SizeRecommendationWithAi result =
            sizeRecommendationService.recommendWithAiLater(member.id(), request.productId(), request.childId());
        return aiResultStream.open(response, SizeRecommendationResponse.from(result.rule()),
            result.ai().thenApply(found -> found.map(SizeRecommendationResponse::from)));
    }
}
