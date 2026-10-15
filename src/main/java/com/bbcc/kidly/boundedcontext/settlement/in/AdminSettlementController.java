package com.bbcc.kidly.boundedcontext.settlement.in;

import com.bbcc.kidly.boundedcontext.settlement.app.SettlementRunService;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementRunRequest;
import com.bbcc.kidly.boundedcontext.settlement.in.dto.SettlementRunResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 정산 실행 API. /api/v1/admin/** 라 ADMIN만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementRunService settlementRunService;

    // 끝날 때까지 기다린다(시연용). 이미 정산한 기준일 · 실행 중이면 409
    @PostMapping("/settlements/run")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementRunResponse run(
        @LoginMember AuthMember admin, @Valid @RequestBody SettlementRunRequest request,
        HttpServletResponse response) {
        var run = settlementRunService.runManually(request.cutoffOn(), admin.id());
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/admin/settlement-runs/" + run.id());
        return SettlementRunResponse.from(run);
    }

    @GetMapping("/settlement-runs/{runId}")
    public SettlementRunResponse getRun(@PathVariable Long runId) {
        return SettlementRunResponse.from(settlementRunService.getRun(runId));
    }
}
