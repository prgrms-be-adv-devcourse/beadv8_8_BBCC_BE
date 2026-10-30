package com.bbcc.kidly.searchservice;

import com.bbcc.kidly.global.security.AccessTokenRevocations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * common의 JWT 검증기는 보낸 토큰이 무효화됐는지 AccessTokenRevocations에 묻는다. 구현은 회원 도메인(회원 DB)이라 이 서비스에는 없다.
 * 검색 서비스에는 로그인이 필요한 API가 없다(목록은 @PublicApi). 그래서 무효화를 확인하지 않는 구현을 둔다:
 * 무효화된 토큰도 서명 · 만료가 맞으면 통과하지만, 공개 API는 토큰이 없어도 같은 응답이라 영향이 없다.
 * 로그인이 필요한 API를 이 서비스에 더하면 이 빈부터 다시 정한다.
 */
@Configuration
class SearchServiceSecurityConfig {

    @Bean
    AccessTokenRevocations accessTokenRevocations() {
        return (memberId, issuedAt) -> false;
    }
}
