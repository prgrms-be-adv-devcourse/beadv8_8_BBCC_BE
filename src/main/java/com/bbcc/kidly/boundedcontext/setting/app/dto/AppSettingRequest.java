package com.bbcc.kidly.boundedcontext.setting.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 설정 변경. 두 값을 모두 보낸다.
 *
 * @param useMock        FE 목 데이터 사용
 * @param demoRoleSwitch FE 시연 역할 전환 표시
 */
public record AppSettingRequest(@NotNull Boolean useMock, @NotNull Boolean demoRoleSwitch) {
}
