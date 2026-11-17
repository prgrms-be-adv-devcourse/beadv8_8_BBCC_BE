package com.bbcc.kidly.boundedcontext.auth.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param redirectUri 콘솔에 등록한 Redirect URI. 프론트가 인가 코드를 받을 때 쓴 값과 같아야 한다
 */
@ConfigurationProperties("kakao")
public record KakaoProperties(String clientId, String clientSecret, String redirectUri) {
}
