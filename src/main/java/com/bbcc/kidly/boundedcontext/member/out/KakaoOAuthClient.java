package com.bbcc.kidly.boundedcontext.member.out;

import com.bbcc.kidly.boundedcontext.member.app.port.OAuthClient;
import com.bbcc.kidly.boundedcontext.member.app.port.OAuthProfile;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.global.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 REST API로 인가 코드를 토큰으로 바꾸고 사용자 정보를 받는다.
 * 인가 코드는 한 번만 쓸 수 있어서 재시도하지 않는다. 카카오 응답 본문은 밖으로 내보내지 않고 로그로만 남긴다.
 * 지원하는 제공자가 카카오뿐이라 provider는 보지 않는다. 제공자가 늘면 제공자별 클래스로 나누고
 * provider로 골라 맡기는 클래스가 {@link OAuthClient}를 구현한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient implements OAuthClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient kakaoRestClient;
    private final KakaoProperties properties;

    @Override
    public OAuthProfile fetchProfile(OAuthProvider provider, String code) {
        var user = fetchUser(fetchAccessToken(code));
        return new OAuthProfile(String.valueOf(user.id()), user.nickname(), user.email());
    }

    private String fetchAccessToken(String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        try {
            return kakaoRestClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoToken.class)
                .accessToken();
        } catch (HttpClientErrorException.BadRequest e) {
            if (isInvalidCode(e)) {
                throw new BusinessException(MemberErrorCode.INVALID_AUTHORIZATION_CODE);
            }
            throw providerError(e);
        } catch (RestClientException e) {
            throw providerError(e);
        }
    }

    private KakaoUser fetchUser(String accessToken) {
        try {
            return kakaoRestClient.get()
                .uri(USER_URL)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(KakaoUser.class);
        } catch (RestClientException e) {
            throw providerError(e);
        }
    }

    // 인가 코드를 이미 썼거나 만료됐으면(KOE320) 다시 로그인하면 된다. 그 밖의 400(redirect URI 불일치 KOE303 등)은 우리 설정 문제라서 제공자 오류로 본다.
    // 본문이 JSON이 아니면(게이트웨이 오류 페이지 등) 읽지 못하므로 인가 코드 오류로 보지 않는다
    private boolean isInvalidCode(HttpClientErrorException e) {
        try {
            var error = e.getResponseBodyAs(KakaoError.class);
            return error != null && "KOE320".equals(error.errorCode());
        } catch (RestClientException unreadable) {
            return false;
        }
    }

    private BusinessException providerError(RestClientException e) {
        log.warn("카카오 로그인 API 호출 실패", e);
        return new BusinessException(MemberErrorCode.OAUTH_PROVIDER_ERROR);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoToken(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoError(@JsonProperty("error_code") String errorCode) {
    }

    // 닉네임은 사용자가 제공에 동의해야 온다. 동의하지 않으면 kakao_account.profile이 없다. 이메일도 제공에 동의해야 온다
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoUser(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

        String nickname() {
            if (kakaoAccount == null || kakaoAccount.profile() == null) {
                return null;
            }
            return kakaoAccount.profile().nickname();
        }

        String email() {
            if (kakaoAccount == null) {
                return null;
            }
            return kakaoAccount.verifiedEmail();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAccount(Profile profile, String email,
        @JsonProperty("is_email_valid") Boolean emailValid,
        @JsonProperty("is_email_verified") Boolean emailVerified) {

        // 만료되거나 인증하지 않은 이메일은 메일이 닿는다고 볼 수 없어 없는 것으로 본다
        String verifiedEmail() {
            if (Boolean.TRUE.equals(emailValid) && Boolean.TRUE.equals(emailVerified)) {
                return email;
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(String nickname) {
    }
}
