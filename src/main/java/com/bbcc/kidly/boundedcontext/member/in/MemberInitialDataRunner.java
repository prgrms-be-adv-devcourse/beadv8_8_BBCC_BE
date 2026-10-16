package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.MemberInitialDataService;
import com.bbcc.kidly.global.config.InitialData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// 자녀(2) · 판매자(3) 초기 데이터가 이 회원을 쓰므로 가장 먼저 넣는다
@InitialData
@Component
@Order(1)
@RequiredArgsConstructor
public class MemberInitialDataRunner implements ApplicationRunner {

    private final MemberInitialDataService memberInitialDataService;

    @Override
    public void run(ApplicationArguments args) {
        memberInitialDataService.load();
    }
}
