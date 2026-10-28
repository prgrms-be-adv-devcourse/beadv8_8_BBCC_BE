package com.bbcc.kidly.boundedcontext.setting.in;

import com.bbcc.kidly.boundedcontext.setting.app.AppSettingService;
import com.bbcc.kidly.boundedcontext.setting.app.dto.AppSettingRequest;
import com.bbcc.kidly.boundedcontext.setting.in.dto.AppSettingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 설정 API. /api/v1/admin/** 라 ADMIN만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminAppSettingController {

    private final AppSettingService appSettingService;

    @PutMapping
    public AppSettingResponse update(@Valid @RequestBody AppSettingRequest request) {
        return AppSettingResponse.from(appSettingService.update(request));
    }
}
