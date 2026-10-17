package com.bbcc.kidly.boundedcontext.setting.app.dto;

import com.bbcc.kidly.boundedcontext.setting.domain.AppSetting;

public record AppSettingResult(boolean useMock, boolean demoRoleSwitch) {

    public static AppSettingResult from(AppSetting setting) {
        return new AppSettingResult(setting.isMockEnabled(), setting.isDemoRoleSwitchEnabled());
    }
}
