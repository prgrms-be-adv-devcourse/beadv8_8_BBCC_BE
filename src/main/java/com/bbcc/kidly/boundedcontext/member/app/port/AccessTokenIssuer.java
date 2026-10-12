package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.global.security.Role;
import java.util.List;

/**
 * 회원 ID와 역할로 Access Token을 발급한다.
 */
public interface AccessTokenIssuer {

    String issue(Long memberId, List<Role> roles);
}
