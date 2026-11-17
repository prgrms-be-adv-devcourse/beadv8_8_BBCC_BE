package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerInitialDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Profile({"local", "dev"})
@Component
@Order(3)
@RequiredArgsConstructor
public class SellerInitialDataRunner implements ApplicationRunner {

    private final SellerInitialDataService sellerInitialDataService;

    @Override
    public void run(ApplicationArguments args) {
        sellerInitialDataService.load();
    }
}
