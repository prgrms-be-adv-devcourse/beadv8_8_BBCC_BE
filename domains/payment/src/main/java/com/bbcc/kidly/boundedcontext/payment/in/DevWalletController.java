package com.bbcc.kidly.boundedcontext.payment.in;

import com.bbcc.kidly.boundedcontext.payment.app.DevChargeService;
import com.bbcc.kidly.boundedcontext.payment.app.dto.DevChargeRequest;
import com.bbcc.kidly.boundedcontext.payment.in.dto.DevChargeResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스 결제창 없이 내 지갑에 예치금을 넣는 개발용 API(#1010).
 * local · test · dev 프로필에서만 등록된다. 개발용 토큰은 어느 회원으로든 받을 수 있어 회원 ID를 따로 받지 않는다.
 */
@Profile({"local", "test", "dev"})
@RestController
@RequiredArgsConstructor
public class DevWalletController {

    private final DevChargeService devChargeService;

    @PostMapping("/api/v1/dev/wallets/me/charges")
    public DevChargeResponse charge(@LoginMember AuthMember member, @Valid @RequestBody DevChargeRequest request) {
        return DevChargeResponse.from(devChargeService.charge(member.id(), request.amount()));
    }
}
