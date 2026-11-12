package com.bbcc.kidly.boundedcontext.recommend.in.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.HomePreviewService;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.HomePreviewWithAi;
import com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto.HomePreviewResponse;
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
 * 로그인한 회원의 자녀에게 홈 화면에서 맛보기로 보여 줄 상품 4개를 돌려주는 API다.
 */
@RestController
@RequiredArgsConstructor
public class HomePreviewController {

    private static final String PATH = "/api/v1/recommendations/products/preview";

    private final HomePreviewService homePreviewService;
    private final AiResultStream aiResultStream;

    @GetMapping(PATH)
    public HomePreviewResponse preview(@LoginMember AuthMember member, @RequestParam Long childId) {
        return HomePreviewResponse.from(homePreviewService.preview(member.id(), childId));
    }

    /**
     * 같은 주소를 스트림(text/event-stream)으로 부르면 AI 답을 기다리지 않는다. 규칙 결과를 rule 이벤트로 먼저 보내고,
     * AI 답이 오면 ai 이벤트를 보낸 뒤 닫는다. 없는 자녀 같은 오류는 스트림을 열기 전에 ProblemDetail로 낸다.
     */
    @GetMapping(value = PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@LoginMember AuthMember member, @RequestParam Long childId, HttpServletResponse response) {
        HomePreviewWithAi result = homePreviewService.previewWithAiLater(member.id(), childId);
        return aiResultStream.open(response, HomePreviewResponse.from(result.rule()),
            result.ai().thenApply(found -> found.map(HomePreviewResponse::from)));
    }
}
