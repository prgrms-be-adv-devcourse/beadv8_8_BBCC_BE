package com.bbcc.kidly.boundedcontext.setting.in.dto;

import com.bbcc.kidly.boundedcontext.setting.app.dto.AppSettingResult;

/**
 * @param useMock FE 목 데이터 사용
 */
public record AppSettingResponse(boolean useMock) {

    public static AppSettingResponse from(AppSettingResult result) {
        return new AppSettingResponse(result.useMock());
    }
}
