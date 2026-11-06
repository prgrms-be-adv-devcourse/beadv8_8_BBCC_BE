package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileInitialDataService;
import org.springframework.context.annotation.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
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
