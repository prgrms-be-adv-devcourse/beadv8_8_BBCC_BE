package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.DevMemberSeedService;
import com.bbcc.kidly.boundedcontext.member.app.dto.DevMemberSeedRequest;
import com.bbcc.kidly.boundedcontext.member.in.dto.DevMemberSeedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 더미 회원 대량 생성 스크립트용. local · test · dev 프로필에서만 등록된다. 운영에 열리면 누구나 회원을 만들 수 있다.
 * key가 같으면 같은 회원을 돌려주므로 몇 번을 불러도 200이고, 새로 만들었는지는 응답의 created로 알린다.
 */
@Profile({"local", "test", "dev"})
@RestController
@RequiredArgsConstructor
public class DevMemberController {

    private final DevMemberSeedService devMemberSeedService;

    @PostMapping("/api/v1/dev/members")
    public DevMemberSeedResponse seed(@Valid @RequestBody DevMemberSeedRequest request) {
        return DevMemberSeedResponse.from(devMemberSeedService.seed(request));
    }
}
