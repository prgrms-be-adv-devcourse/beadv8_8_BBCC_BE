package com.bbcc.kidly.boundedcontext.setting;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.setting.domain.AppSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppSettingTest {

    @Test
    @DisplayName("기본 설정은 목 데이터와 시연 역할 전환을 켠다")
    void defaults() {
        AppSetting setting = AppSetting.defaults();

        assertThat(setting.isMockEnabled()).isTrue();
        assertThat(setting.isDemoRoleSwitchEnabled()).isTrue();
    }

    @Test
    @DisplayName("바꾸면 받은 값으로 바꾼다")
    void change() {
        AppSetting setting = AppSetting.defaults();

        setting.change(false, false);

        assertThat(setting.isMockEnabled()).isFalse();
        assertThat(setting.isDemoRoleSwitchEnabled()).isFalse();
    }

    @Test
    @DisplayName("받지 않은 값(null)은 그대로 둔다")
    void changeOnlyGiven() {
        AppSetting setting = AppSetting.defaults();

        setting.change(null, false);

        assertThat(setting.isMockEnabled()).isTrue();
        assertThat(setting.isDemoRoleSwitchEnabled()).isFalse();
    }
}
