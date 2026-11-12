package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 더미 회원 입력. 같은 key는 같은 회원이다. 닉네임은 앞뒤 공백을 뺀 값으로 검사한다.
 */
public record DevMemberSeedRequest(
    @NotBlank @Size(max = 40) @Pattern(regexp = "[a-zA-Z0-9-]*") String key,
    @NotBlank @Size(min = Nickname.MIN_LENGTH, max = Nickname.MAX_LENGTH) String nickname) {

    public DevMemberSeedRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
