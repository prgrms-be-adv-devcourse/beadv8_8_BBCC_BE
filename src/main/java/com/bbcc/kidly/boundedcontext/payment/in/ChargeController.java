package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.ChargeConfirmService;
import com.bbcc.kidly.boundedcontext.payment.app.ChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeConfirmResult;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.app.dto.ConfirmChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.in.dto.ChargeConfirmResponse;
import com.bbcc.kidly.boundedcontext.payment.in.dto.ChargeDetailResponse;
import com.bbcc.kidly.boundedcontext.payment.in.dto.ChargeRequestResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예치금 충전. 요청 → 토스 결제창 → 승인 순서다.
 */
@RestController
@RequestMapping(ChargeController.CHARGES_PATH)
@RequiredArgsConstructor
public class ChargeController {

    static final String CHARGES_PATH = "/api/v1/wallets/me/charges";

    private final ChargeService chargeService;
    private final ChargeConfirmService chargeConfirmService;

    @PostMapping
    public ResponseEntity<ChargeRequestResponse> request(
        @LoginMember AuthMember member, @Valid @RequestBody ChargeRequest request) {
        ChargeRequestResponse body = ChargeRequestResponse.from(chargeService.request(member.id(), request));
        return ResponseEntity.created(URI.create(CHARGES_PATH + "/" + body.chargeId())).body(body);
    }

    /**
     * 입금까지 끝나면 200, 토스 결과를 아직 모르면 202. 202면 충전 조회로 상태를 다시 확인한다.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ChargeConfirmResponse> confirm(
        @LoginMember AuthMember member, @Valid @RequestBody ConfirmChargeRequest request) {
        ChargeConfirmResult result = chargeConfirmService.confirm(member.id(), request);
        ChargeConfirmResponse body = ChargeConfirmResponse.from(result);
        return result.confirming()
            ? ResponseEntity.accepted().body(body)
            : ResponseEntity.ok(body);
    }

    /**
     * 내 충전 내역. 상태와 상관없이 모두 최신순으로 준다.
     */
    @GetMapping
    public PageResponse<ChargeDetailResponse> getCharges(
        @LoginMember AuthMember member,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(chargeService.getCharges(member.id(), page, size).map(ChargeDetailResponse::from));
    }

    @GetMapping("/{chargeId}")
    public ChargeDetailResponse get(@LoginMember AuthMember member, @PathVariable Long chargeId) {
        return ChargeDetailResponse.from(chargeService.get(member.id(), chargeId));
    }
}
