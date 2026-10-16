package com.bbcc.kidly.boundedcontext.setting.app.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 설정 변경.
 *
 * @param useMock FE 목 데이터 사용
 */
public record AppSettingRequest(@NotNull Boolean useMock) {
}
