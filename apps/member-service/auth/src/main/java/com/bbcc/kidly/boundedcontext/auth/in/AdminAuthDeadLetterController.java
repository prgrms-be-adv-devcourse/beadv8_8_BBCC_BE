package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.AuthDeadLetterService;
import com.bbcc.kidly.boundedcontext.auth.in.dto.AuthDeadLetterResponse;
import com.bbcc.kidly.member.support.response.PageResponse;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth-dead-letters")
@RequiredArgsConstructor
public class AdminAuthDeadLetterController {

    private final AuthDeadLetterService service;

    @GetMapping
    public PageResponse<AuthDeadLetterResponse> list(
        @RequestParam(defaultValue = "OPEN") @Pattern(regexp = "OPEN|REPROCESSED") String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(status, page, size).map(AuthDeadLetterResponse::from));
    }

    @GetMapping("/{id}")
    public AuthDeadLetterResponse get(@PathVariable Long id) {
        return AuthDeadLetterResponse.from(service.get(id));
    }

    /**
     * 처리 실패도 200/OPEN으로 이력을 반환한다. 이미 성공했으면 재실행 없이 200이다.
     */
    @PostMapping("/{id}/reprocess")
    public AuthDeadLetterResponse reprocess(@PathVariable Long id, @LoginAdmin AuthAdmin admin) {
        return AuthDeadLetterResponse.from(service.reprocess(id, admin.id()));
    }
}
