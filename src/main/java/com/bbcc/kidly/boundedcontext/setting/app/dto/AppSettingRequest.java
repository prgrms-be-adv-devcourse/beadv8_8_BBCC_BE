package com.bbcc.kidly.boundedcontext.setting.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

/**
 * 설정 변경. 보낸 값만 바꾸고, 빠진 값은 그대로 둔다.
 *
 * @param useMock        FE 목 데이터 사용
 * @param demoRoleSwitch FE 시연 역할 전환 (시연 콘솔 · API 흐름 · 시연 표시 포함)
 */
public record AppSettingRequest(Boolean useMock, Boolean demoRoleSwitch) {

    @JsonIgnore
    @AssertTrue(message = "바꿀 설정(useMock · demoRoleSwitch)을 하나 이상 보내야 합니다")
    public boolean isAnyValuePresent() {
        return useMock != null || demoRoleSwitch != null;
    }
}
