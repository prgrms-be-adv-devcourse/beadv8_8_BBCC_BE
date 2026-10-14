package com.bbcc.kidly.boundedcontext.member.app.dto;

import com.bbcc.kidly.boundedcontext.member.domain.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 입력. 지금은 닉네임만 바꿀 수 있다. 앞뒤 공백을 뺀 값으로 검사한다.
 */
public record MemberUpdateRequest(
    @NotBlank @Size(min = Member.NICKNAME_MIN_LENGTH, max = Member.NICKNAME_MAX_LENGTH) String nickname) {

    public MemberUpdateRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
