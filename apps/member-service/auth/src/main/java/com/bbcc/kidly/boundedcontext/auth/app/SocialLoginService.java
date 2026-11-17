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

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService implements SocialAccountFinder {

    private final SocialAccountRepository socialAccountRepository;
    private final MemberAccounts memberAccounts;
    private final OAuthClient oauthClient;

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
