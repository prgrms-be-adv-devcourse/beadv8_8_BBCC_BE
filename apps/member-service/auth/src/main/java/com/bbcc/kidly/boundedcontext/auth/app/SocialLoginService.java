package com.bbcc.kidly.boundedcontext.auth.app;

import com.bbcc.kidly.boundedcontext.auth.app.dto.SocialLoginRequest;
import com.bbcc.kidly.boundedcontext.auth.app.port.OAuthClient;
import com.bbcc.kidly.boundedcontext.auth.app.port.OAuthProfile;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccountRepository;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialIdentity;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.auth.out.SocialAccountFinder;
import com.bbcc.kidly.shared.member.dto.MemberLoginState;
import com.bbcc.kidly.shared.member.dto.MemberRegistration;
import com.bbcc.kidly.shared.member.out.MemberAccounts;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 소셜 계정으로 로그인한다. 처음이면 가입시킨다. 토큰은 {@link AuthService}가 발급한다.
 * 클래스에 트랜잭션을 걸지 않는다. 유니크 충돌로 롤백된 뒤 다시 조회할 수 있어야 하고,
 * 소셜 제공자 호출이 트랜잭션 밖이라야 제공자가 느려도 DB 커넥션을 붙잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService implements SocialAccountFinder {

    private final SocialAccountRepository socialAccountRepository;
    private final MemberAccounts memberAccounts;
    private final OAuthClient oauthClient;

    // provider는 URL 경로의 소문자 이름(kakao)이다
    public MemberLoginState signIn(String providerName, SocialLoginRequest request) {
        var provider = OAuthProvider.from(providerName);
        var profile = oauthClient.fetchProfile(provider, request.code());
        var identity = new SocialIdentity(provider, profile.providerUserId());
        var account = socialAccountRepository.findByIdentity(identity);
        var linkedMemberId = account.flatMap(SocialAccount::linkedMemberId);
        if (linkedMemberId.isPresent()) {
            var state = memberAccounts.findLoginState(linkedMemberId.get())
                .orElseThrow(() -> new IllegalStateException("연결된 회원이 없다: memberId=" + linkedMemberId.get()));
            if (!(state instanceof MemberLoginState.Withdrawn)) {
                return state;
            }
            socialAccountRepository.deleteById(account.get().getId());
            account = Optional.empty();
        }
        return register(account, identity, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findProviders(Long memberId) {
        return socialAccountRepository.findByMemberId(memberId).stream()
            .map(account -> account.getIdentity().provider().name())
            .toList();
    }

    @Transactional
    public void unlinkAll(Long memberId) {
        socialAccountRepository.deleteByMemberId(memberId);
    }

    private MemberLoginState register(Optional<SocialAccount> claimed, SocialIdentity identity, OAuthProfile profile) {
        // 이메일은 가입할 때만 필수다. 이메일을 받기 전에 가입한 회원은 이메일 없이도 로그인한다
        if (!StringUtils.hasText(profile.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_REQUIRED);
        }
        var claim = claimed.orElseGet(() -> claim(identity));
        var state = memberAccounts.register(
            new MemberRegistration(claim.registrationKey(), profile.nickname(), profile.email()));
        socialAccountRepository.link(claim.getId(), state.memberId());
        log.info("소셜 계정 연결: memberId={} provider={}", state.memberId(), identity.provider());
        return state;
    }

    private SocialAccount claim(SocialIdentity identity) {
        try {
            return socialAccountRepository.save(SocialAccount.claim(identity, LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            return socialAccountRepository.findByIdentity(identity).orElseThrow(() -> e);
        }
    }
}
