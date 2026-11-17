package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberInitialDataService {

    private static final Long ADMIN_ID = 1L;

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;
    private final ShippingAddressService shippingAddressService;
    private final AdminMemberService adminMemberService;

    @Transactional
    public void load() {
        if (memberRepository.count() > 0) {
            log.info("회원이 이미 있어 회원 초기 데이터를 넣지 않는다");
            return;
        }
        var parent = register(1, "테스트보호자", "parent@kidly.test");
        register(2, "테스트판매자", "seller@kidly.test");
        register(3, "테스트관리자", "admin@kidly.test");
        var suspended = register(4, "정지회원", "suspended@kidly.test");
        register(5, "신청대기회원", "pending-seller@kidly.test");
        register(6, "신청거절회원", "rejected-seller@kidly.test");

        shippingAddressService.register(parent.getId(),
            new ShippingAddressRequest("홍길동", "010-0000-0000", "00000", "서울시 테스트로 1", null, true));
        adminMemberService.suspend(suspended.getId(), ADMIN_ID,
            new MemberSuspendRequest("정지 화면 확인용 초기 데이터"));
        log.info("회원 초기 데이터를 넣었다");
    }

    private Member register(int number, String nickname, String email) {
        return memberRegistrar.register(new MemberRegisterInfo("seed:" + number, new Nickname(nickname), email));
    }
}
