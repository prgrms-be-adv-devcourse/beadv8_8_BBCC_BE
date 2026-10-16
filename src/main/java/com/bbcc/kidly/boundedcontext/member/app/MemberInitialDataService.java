package com.bbcc.kidly.boundedcontext.member.app;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberSuspendRequest;
import com.bbcc.kidly.boundedcontext.member.app.dto.ShippingAddressRequest;
import com.bbcc.kidly.boundedcontext.member.app.support.MemberRegistrar;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRegisterInfo;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기획서 7.1 공통 초기 데이터 중 회원 · 배송지를 넣는다. 자녀 · 판매자 초기 데이터가 여기서 만든 회원 ID를 쓴다.
 * ID가 1부터 매겨지도록 회원이 한 명도 없을 때만 넣는다. 가입 이벤트로 지갑도 생긴다.
 * 회원을 지운 DB는 ID 시퀀스가 1로 돌아가지 않으므로 DB를 새로 만들어야 ID가 맞는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberInitialDataService {

    private final MemberRepository memberRepository;
    private final MemberRegistrar memberRegistrar;
    private final ShippingAddressService shippingAddressService;
    private final AdminMemberService adminMemberService;

    // 가입 순서가 회원 ID가 된다
    @Transactional
    public void load() {
        if (memberRepository.count() > 0) {
            log.info("회원이 이미 있어 회원 초기 데이터를 넣지 않는다");
            return;
        }
        var parent = register(1, "테스트보호자", "parent@kidly.test");
        register(2, "테스트판매자", "seller@kidly.test");
        var admin = register(3, "테스트관리자", "admin@kidly.test");
        var suspended = register(4, "정지회원", "suspended@kidly.test");
        register(5, "신청대기회원", "pending-seller@kidly.test");
        register(6, "신청거절회원", "rejected-seller@kidly.test");

        shippingAddressService.register(parent.getId(),
            new ShippingAddressRequest("홍길동", "010-0000-0000", "00000", "서울시 테스트로 1", null, true));
        adminMemberService.grantAdmin(admin.getId());
        adminMemberService.suspend(suspended.getId(), admin.getId(),
            new MemberSuspendRequest("정지 화면 확인용 초기 데이터"));
        log.info("회원 초기 데이터를 넣었다");
    }

    // 소셜 계정 ID는 카카오 회원번호(숫자)와 겹치지 않게 seed-번호로 둔다
    private Member register(int number, String nickname, String email) {
        return memberRegistrar.register(
            new MemberRegisterInfo(new SocialIdentity(OAuthProvider.KAKAO, "seed-" + number), nickname, email));
    }
}
