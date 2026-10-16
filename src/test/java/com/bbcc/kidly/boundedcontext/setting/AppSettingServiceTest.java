package com.bbcc.kidly.boundedcontext.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bbcc.kidly.boundedcontext.setting.app.AppSettingService;
import com.bbcc.kidly.boundedcontext.setting.app.dto.AppSettingRequest;
import com.bbcc.kidly.boundedcontext.setting.app.dto.AppSettingResult;
import com.bbcc.kidly.boundedcontext.setting.domain.AppSetting;
import com.bbcc.kidly.boundedcontext.setting.domain.AppSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppSettingServiceTest {

    @Mock
    private AppSettingRepository appSettingRepository;

    @InjectMocks
    private AppSettingService appSettingService;

    @Test
    @DisplayName("저장된 설정을 준다")
    void get() {
        AppSetting stored = AppSetting.defaults();
        stored.change(false);
        given(appSettingRepository.findById(AppSetting.ID)).willReturn(Optional.of(stored));

        assertThat(appSettingService.get()).isEqualTo(new AppSettingResult(false));
    }

    @Test
    @DisplayName("저장된 설정이 없으면 기본값(켬)을 준다")
    void getDefaults() {
        given(appSettingRepository.findById(AppSetting.ID)).willReturn(Optional.empty());

        assertThat(appSettingService.get()).isEqualTo(new AppSettingResult(true));
    }

    @Test
    @DisplayName("저장된 설정을 바꾸고 바뀐 값을 준다")
    void update() {
        AppSetting stored = AppSetting.defaults();
        given(appSettingRepository.findById(AppSetting.ID)).willReturn(Optional.of(stored));

        AppSettingResult result = appSettingService.update(new AppSettingRequest(false));

        assertThat(result).isEqualTo(new AppSettingResult(false));
        assertThat(stored.isMockEnabled()).isFalse();
        then(appSettingRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("저장된 설정이 없으면 받은 값으로 새로 저장한다")
    void updateCreates() {
        given(appSettingRepository.findById(AppSetting.ID)).willReturn(Optional.empty());
        given(appSettingRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AppSettingResult result = appSettingService.update(new AppSettingRequest(false));

        ArgumentCaptor<AppSetting> saved = ArgumentCaptor.forClass(AppSetting.class);
        then(appSettingRepository).should().save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(AppSetting.ID);
        assertThat(saved.getValue().isMockEnabled()).isFalse();
        assertThat(result).isEqualTo(new AppSettingResult(false));
    }
}
