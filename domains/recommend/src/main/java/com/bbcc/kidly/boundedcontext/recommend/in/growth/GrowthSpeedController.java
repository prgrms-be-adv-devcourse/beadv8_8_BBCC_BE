package com.bbcc.kidly.boundedcontext.recommend.in.growth;

import com.bbcc.kidly.boundedcontext.recommend.app.growth.GrowthSpeedService;
import com.bbcc.kidly.boundedcontext.recommend.in.growth.dto.GrowthSpeedResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 없이 성장 속도를 보여 주는 화면(자녀 상세의 성장 그래프)이 사이즈 추천과 같은 값을 쓰도록 자녀 단위로 내려 준다.
 */
@RestController
@RequiredArgsConstructor
public class GrowthSpeedController {

    private final GrowthSpeedService growthSpeedService;

    @GetMapping("/api/v1/recommendations/children/{childId}/growth-speed")
    public GrowthSpeedResponse find(@LoginMember AuthMember member, @PathVariable Long childId) {
        return GrowthSpeedResponse.from(growthSpeedService.find(member.id(), childId));
    }
}
