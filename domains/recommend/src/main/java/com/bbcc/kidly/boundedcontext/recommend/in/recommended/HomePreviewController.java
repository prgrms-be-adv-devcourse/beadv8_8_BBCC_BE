package com.bbcc.kidly.boundedcontext.recommend.in.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.HomePreviewService;
import com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto.HomePreviewResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원의 자녀에게 홈 화면에서 맛보기로 보여 줄 상품 4개를 돌려주는 API다.
 */
@RestController
@RequiredArgsConstructor
public class HomePreviewController {

    private final HomePreviewService homePreviewService;

    @GetMapping("/api/v1/recommendations/products/preview")
    public HomePreviewResponse preview(@LoginMember AuthMember member, @RequestParam Long childId) {
        return HomePreviewResponse.from(homePreviewService.preview(member.id(), childId));
    }
}
