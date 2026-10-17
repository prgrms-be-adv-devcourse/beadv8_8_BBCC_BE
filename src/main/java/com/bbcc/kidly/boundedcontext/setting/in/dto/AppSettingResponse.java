package com.bbcc.kidly.boundedcontext.setting.in.dto;

import com.bbcc.kidly.boundedcontext.setting.app.dto.AppSettingResult;

/**
 * @param useMock        FE 목 데이터 사용
 * @param demoRoleSwitch FE 시연 역할 전환 (시연 콘솔 · API 흐름 · 시연 표시 포함)
 */
public record AppSettingResponse(boolean useMock, boolean demoRoleSwitch) {

    public static AppSettingResponse from(AppSettingResult result) {
        return new AppSettingResponse(result.useMock(), result.demoRoleSwitch());
    }
}
