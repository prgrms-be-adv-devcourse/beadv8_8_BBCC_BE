package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberLoginRequest;
import com.bbcc.kidly.boundedcontext.member.app.port.MemberQueryRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 소셜 계정으로 로그인한다. 처음이면 가입시킨다. 토큰은 {@link AuthTokenService}가 발급한다.
 * 트랜잭션을 걸지 않는다. 동시 가입으로 가입 트랜잭션이 롤백돼도 다시 조회할 수 있어야 하기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final MemberQueryRepository memberQueryRepository;
    private final MemberRegistrar memberRegistrar;

    public Member login(MemberLoginRequest request) {
        var member = memberQueryRepository.findBySocialAccount(request.provider(), request.providerUserId())
            .orElseGet(() -> register(request));
        if (!member.canLogin()) {
            throw new BusinessException(MemberErrorCode.LOGIN_NOT_ALLOWED);
        }
        return member;
    }

    // 같은 소셜 계정으로 동시에 처음 로그인하면 한 요청만 가입되고, 나머지는 그 회원으로 로그인한다
    private Member register(MemberLoginRequest request) {
        try {
            return memberRegistrar.register(request);
        } catch (DataIntegrityViolationException e) {
            return memberQueryRepository.findBySocialAccount(request.provider(), request.providerUserId())
                .orElseThrow(() -> e);
        }
    }
}
