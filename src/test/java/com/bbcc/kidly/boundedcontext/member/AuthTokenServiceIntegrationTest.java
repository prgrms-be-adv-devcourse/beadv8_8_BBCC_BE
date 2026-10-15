package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.app.AuthTokenService;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRole;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class AuthTokenServiceIntegrationTest {

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("관리자 이메일 목록에 없는 ADMIN은 토큰을 받을 때 USER로 저장된다")
    void savesDemotedRole() {
        // 테스트 프로필은 관리자 이메일을 설정하지 않는다
        var admin = memberRepository.save(MemberFixture.withRole(MemberRole.ADMIN));

        authTokenService.issueAccessToken(admin.getId());

        assertThat(memberRepository.getById(admin.getId()).getRole()).isEqualTo(MemberRole.USER);
    }
}
