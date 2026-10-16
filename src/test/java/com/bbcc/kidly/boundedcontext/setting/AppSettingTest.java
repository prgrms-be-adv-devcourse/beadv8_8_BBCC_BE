package com.bbcc.kidly.boundedcontext.setting;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.setting.domain.AppSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppSettingTest {

    @Test
    @DisplayName("기본 설정은 목 데이터 · 시연 역할 전환을 모두 켠다")
    void defaults() {
        AppSetting setting = AppSetting.defaults();

        assertThat(setting.isMockEnabled()).isTrue();
        assertThat(setting.isDemoRoleSwitchEnabled()).isTrue();
    }

    @Test
    @DisplayName("바꾸면 두 값을 받은 대로 바꾼다")
    void change() {
        AppSetting setting = AppSetting.defaults();

        setting.change(false, true);

        assertThat(setting.isMockEnabled()).isFalse();
        assertThat(setting.isDemoRoleSwitchEnabled()).isTrue();
    }
}
