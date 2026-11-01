package com.bbcc.kidly.boundedcontext.setting.in;

import com.bbcc.kidly.boundedcontext.setting.app.AppSettingService;
import com.bbcc.kidly.boundedcontext.setting.in.dto.AppSettingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FE가 시작할 때 읽는 동작 설정. 로그인 전 화면도 이 값으로 그리므로 공개한다.
 */
@RestController
@RequiredArgsConstructor
public class AppSettingController {

    private final AppSettingService appSettingService;

    @GetMapping("/api/v1/settings")
    public AppSettingResponse get() {
        return AppSettingResponse.from(appSettingService.get());
    }
}
