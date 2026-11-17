package com.bbcc.kidly.boundedcontext.member.in;

import com.bbcc.kidly.boundedcontext.member.app.MemberInitialDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
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
