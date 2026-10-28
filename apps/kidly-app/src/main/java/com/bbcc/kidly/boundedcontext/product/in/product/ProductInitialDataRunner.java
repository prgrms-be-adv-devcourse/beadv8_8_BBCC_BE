package com.bbcc.kidly.boundedcontext.product.in.product;

import com.bbcc.kidly.boundedcontext.product.app.product.ProductSampleDataService;
import com.bbcc.kidly.boundedcontext.product.app.type.ProductReferenceDataService;
import com.bbcc.kidly.global.config.InitialData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// 판매자 초기 데이터(3)의 판매자 2가 샘플 상품을 판다. 샘플 상품이 기준 데이터를 쓰므로 기준 데이터를 먼저 넣는다
@InitialData
@Component
@Order(4)
@RequiredArgsConstructor
public class ProductInitialDataRunner implements ApplicationRunner {

    private final ProductReferenceDataService productReferenceDataService;
    private final ProductSampleDataService productSampleDataService;

    @Override
    public void run(ApplicationArguments args) {
        productReferenceDataService.load();
        productSampleDataService.load();
    }
}
