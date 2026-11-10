package com.bbcc.kidly.boundedcontext.recommend.in.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.ReferenceGarmentService;
import com.bbcc.kidly.boundedcontext.recommend.in.referencegarment.dto.ReferenceGarmentResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 보호자가 자녀별 기준 옷(지금 잘 맞게 입는 상의 · 하의 한 벌)을 조회 · 저장 · 삭제하는 API다.
 */
@RestController
@RequiredArgsConstructor
public class ReferenceGarmentController {

    private static final String REFERENCE_GARMENTS = "/api/v1/recommendations/children/{childId}/reference-garments";

    private final ReferenceGarmentService referenceGarmentService;

    @GetMapping(REFERENCE_GARMENTS)
    public List<ReferenceGarmentResponse> list(@LoginMember AuthMember member, @PathVariable Long childId) {
        return referenceGarmentService.list(member.id(), childId).stream()
            .map(ReferenceGarmentResponse::from)
            .toList();
    }
}
