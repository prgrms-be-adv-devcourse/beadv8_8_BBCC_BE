package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.AdminBootstrapProperties;
import com.bbcc.kidly.boundedcontext.auth.app.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;
    private final AdminBootstrapProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrapService.bootstrap(properties.email(), properties.password());
    }
}
