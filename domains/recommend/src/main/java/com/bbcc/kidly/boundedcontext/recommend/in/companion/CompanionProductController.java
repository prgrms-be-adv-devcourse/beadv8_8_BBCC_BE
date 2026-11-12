package com.bbcc.kidly.boundedcontext.recommend.in.companion;

import com.bbcc.kidly.boundedcontext.recommend.app.companion.CompanionProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.companion.dto.CompanionProductsWithAi;
import com.bbcc.kidly.boundedcontext.recommend.in.companion.dto.CompanionProductsResponse;
import com.bbcc.kidly.boundedcontext.recommend.in.support.AiResultStream;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 상품 상세에서 고른 자녀에게 맞는 사이즈로, 보고 있는 상품과 함께 사면 좋은 상품을 4개까지 돌려주는 API다.
 */
@RestController
@RequiredArgsConstructor
public class CompanionProductController {

    private static final String PATH = "/api/v1/recommendations/companions";

    private final CompanionProductService companionProductService;
    private final AiResultStream aiResultStream;

    @GetMapping(PATH)
    public CompanionProductsResponse list(
        @LoginMember AuthMember member,
        @RequestParam Long productId,
        @RequestParam Long childId) {
        return CompanionProductsResponse.from(companionProductService.recommend(member.id(), productId, childId));
    }

    /**
     * 같은 주소를 스트림(text/event-stream)으로 부르면 AI 답을 기다리지 않는다. 규칙 결과를 rule 이벤트로 먼저 보내고,
     * AI 답이 오면 ai 이벤트를 보낸 뒤 닫는다. 없는 상품 · 다른 회원의 자녀 같은 오류는 스트림을 열기 전에 ProblemDetail로 낸다.
     */
    @GetMapping(value = PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
        @LoginMember AuthMember member,
        @RequestParam Long productId,
        @RequestParam Long childId,
        HttpServletResponse response) {
        CompanionProductsWithAi result = companionProductService.recommendWithAiLater(member.id(), productId, childId);
        return aiResultStream.open(response, CompanionProductsResponse.from(result.rule()),
            result.ai().thenApply(found -> found.map(CompanionProductsResponse::from)));
    }
}
