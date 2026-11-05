package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerInitialDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// 회원 초기 데이터(1)의 회원 2 · 3 · 5 · 6을 쓴다. 상품 초기 데이터는 이 판매자 뒤(4~)에 넣는다
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
