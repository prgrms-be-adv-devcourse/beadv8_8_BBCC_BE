package com.bbcc.kidly.boundedcontext.review.in.web.admin;

import com.bbcc.kidly.boundedcontext.review.app.CommunityDeadLetterService;
import com.bbcc.kidly.boundedcontext.review.in.web.admin.dto.CommunityDeadLetterResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/community/dead-letters")
@RequiredArgsConstructor
public class AdminCommunityDeadLetterController {

    private final CommunityDeadLetterService service;

    @GetMapping
    public PageResponse<CommunityDeadLetterResponse> list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(status, page, size).map(CommunityDeadLetterResponse::from));
    }

    @GetMapping("/{id}")
    public CommunityDeadLetterResponse get(@PathVariable Long id) {
        return CommunityDeadLetterResponse.from(service.get(id));
    }

    /**
     * 처리 실패도 200/OPEN으로 이력을 반환한다. 이미 성공했으면 재실행 없이 200이다.
     */
    @PostMapping("/{id}/reprocess")
    public CommunityDeadLetterResponse reprocess(@PathVariable Long id, @LoginMember AuthMember admin) {
        return CommunityDeadLetterResponse.from(service.reprocess(id, admin.id()));
    }
}
