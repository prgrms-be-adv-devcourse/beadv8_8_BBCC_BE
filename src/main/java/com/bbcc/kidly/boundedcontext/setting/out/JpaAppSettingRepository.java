package com.bbcc.kidly.boundedcontext.setting.out;

import com.bbcc.kidly.boundedcontext.setting.domain.AppSetting;
import com.bbcc.kidly.boundedcontext.setting.domain.AppSettingRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAppSettingRepository extends JpaRepository<AppSetting, Long>, AppSettingRepository {
}
