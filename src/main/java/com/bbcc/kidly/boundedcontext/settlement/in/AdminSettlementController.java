package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.AdminSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.AdminSettlementSearchRequest;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunRequest;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.AdminSettlementSummaryResponse;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementRunResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 정산 API. /api/v1/admin/** 라 ADMIN만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementRunService settlementRunService;
    private final AdminSettlementService adminSettlementService;

    @GetMapping("/settlements")
    public PageResponse<AdminSettlementSummaryResponse> settlements(
        @Valid @ModelAttribute AdminSettlementSearchRequest request, @PageableDefault Pageable pageable) {
        return PageResponse.from(
            adminSettlementService.getSettlements(request, pageable).map(AdminSettlementSummaryResponse::from));
    }

    // 지급 결과를 받지 못한 정산서만. 결과는 결제가 이벤트로 돌려준다
    @PostMapping("/settlements/{settlementId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryPayout(@PathVariable Long settlementId) {
        adminSettlementService.retryPayout(settlementId);
    }

    // 끝날 때까지 기다린다(시연용). 이미 정산한 기준일 · 실행 중이면 409
    @PostMapping("/settlements/run")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementRunResponse run(
        @LoginMember AuthMember admin, @Valid @RequestBody SettlementRunRequest request,
        HttpServletResponse response) {
        var run = settlementRunService.runManually(request.cutoffOn(), admin.id());
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/admin/settlement-runs/" + run.runId());
        return SettlementRunResponse.from(run);
    }

    @GetMapping("/settlement-runs/{runId}")
    public SettlementRunResponse getRun(@PathVariable Long runId) {
        return SettlementRunResponse.from(settlementRunService.getRun(runId));
    }
}
