package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.shared.auth.Role;
import java.util.List;

/**
 * 회원 ID · 닉네임 · 역할로 Access Token을 발급한다.
 */
public interface AccessTokenIssuer {

    String issue(Long memberId, String nickname, List<Role> roles);
}
