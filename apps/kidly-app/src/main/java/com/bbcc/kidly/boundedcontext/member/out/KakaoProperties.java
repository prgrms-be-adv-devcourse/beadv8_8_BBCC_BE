package com.bbcc.kidly.boundedcontext.member.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로그인 설정. 값은 카카오 개발자 콘솔에서 받는다.
 *
 * @param clientId REST API 키
 * @param redirectUri 콘솔에 등록한 Redirect URI. 프론트가 인가 코드를 받을 때 쓴 값과 같아야 한다
 */
@ConfigurationProperties("kakao")
public record KakaoProperties(String clientId, String clientSecret, String redirectUri) {
}
