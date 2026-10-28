package com.bbcc.kidly.boundedcontext.setting.domain;

import java.util.Optional;

public interface AppSettingRepository {

    Optional<AppSetting> findById(Long id);

    AppSetting save(AppSetting setting);
}
