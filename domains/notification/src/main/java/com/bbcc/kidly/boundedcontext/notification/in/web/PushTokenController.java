package com.bbcc.kidly.boundedcontext.notification.in.web;

import com.bbcc.kidly.boundedcontext.notification.app.PushTokenService;
import com.bbcc.kidly.boundedcontext.notification.in.web.dto.PushTokenRequest;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 브라우저의 웹 푸시 대상 FID 등록, 해제. 회원 로그인 필요. FID는 URL에 남지 않게 본문으로 받는다.
 */
@RestController
@RequestMapping("/api/v1/notifications/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@LoginMember AuthMember member, @Valid @RequestBody PushTokenRequest request) {
        pushTokenService.register(member.id(), request.fid());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@LoginMember AuthMember member, @Valid @RequestBody PushTokenRequest request) {
        pushTokenService.unregister(member.id(), request.fid());
    }
}
