package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileInitialDataService;
import com.bbcc.kidly.global.config.InitialData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// 회원 초기 데이터(1)가 만든 보호자에게 붙인다
@InitialData
@Component
@Order(2)
@RequiredArgsConstructor
public class ChildProfileInitialDataRunner implements ApplicationRunner {

    private final ChildProfileInitialDataService childProfileInitialDataService;

    @Override
    public void run(ApplicationArguments args) {
        childProfileInitialDataService.load();
    }
}
