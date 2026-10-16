package com.bbcc.kidly.boundedcontext.setting;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.setting.domain.AppSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AppSettingTest {

    @Test
    @DisplayName("기본 설정은 목 데이터를 켠다")
    void defaults() {
        assertThat(AppSetting.defaults().isMockEnabled()).isTrue();
    }

    @Test
    @DisplayName("바꾸면 받은 값으로 바꾼다")
    void change() {
        AppSetting setting = AppSetting.defaults();

        setting.change(false);

        assertThat(setting.isMockEnabled()).isFalse();
    }
}
