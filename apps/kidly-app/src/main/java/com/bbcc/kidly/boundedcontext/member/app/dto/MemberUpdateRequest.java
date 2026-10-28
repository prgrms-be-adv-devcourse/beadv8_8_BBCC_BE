package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 입력. 지금은 닉네임만 바꿀 수 있다. 앞뒤 공백을 뺀 값으로 검사한다.
 */
public record MemberUpdateRequest(
    @NotBlank @Size(min = Nickname.MIN_LENGTH, max = Nickname.MAX_LENGTH) String nickname) {

    public MemberUpdateRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
