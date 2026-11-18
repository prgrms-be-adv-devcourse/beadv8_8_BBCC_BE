package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.AuthDeadLetterService;
import com.bbcc.kidly.member.support.deadletter.DeadLetterResponse;
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
    public PageResponse<DeadLetterResponse> list(
        @RequestParam(defaultValue = "OPEN") @Pattern(regexp = "OPEN|REPROCESSED") String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(status, page, size).map(DeadLetterResponse::from));
    }

    @GetMapping("/{id}")
    public DeadLetterResponse get(@PathVariable Long id) {
        return DeadLetterResponse.from(service.get(id));
    }

    @PostMapping("/{id}/reprocess")
    public DeadLetterResponse reprocess(@PathVariable Long id, @LoginAdmin AuthAdmin admin) {
        return DeadLetterResponse.from(service.reprocess(id, admin.id()));
    }
}
