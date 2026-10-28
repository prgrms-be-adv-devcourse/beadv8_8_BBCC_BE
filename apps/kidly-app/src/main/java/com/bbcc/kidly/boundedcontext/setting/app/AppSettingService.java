package com.bbcc.kidly.boundedcontext.setting.app;

import com.bbcc.kidly.boundedcontext.setting.app.dto.AppSettingRequest;
import com.bbcc.kidly.boundedcontext.setting.app.dto.AppSettingResult;
import com.bbcc.kidly.boundedcontext.setting.domain.AppSetting;
import com.bbcc.kidly.boundedcontext.setting.domain.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FE 동작 설정을 조회 · 변경한다. 저장된 행이 없으면 기본값으로 보고, 처음 바꿀 때 저장한다.
 */
@Service
@RequiredArgsConstructor
public class AppSettingService {

    private final AppSettingRepository appSettingRepository;

    @Transactional(readOnly = true)
    public AppSettingResult get() {
        return AppSettingResult.from(appSettingRepository.findById(AppSetting.ID).orElseGet(AppSetting::defaults));
    }

    @Transactional
    public AppSettingResult update(AppSettingRequest request) {
        AppSetting setting = appSettingRepository.findById(AppSetting.ID)
            .orElseGet(() -> appSettingRepository.save(AppSetting.defaults()));
        setting.change(request.useMock(), request.demoRoleSwitch());
        return AppSettingResult.from(setting);
    }
}
