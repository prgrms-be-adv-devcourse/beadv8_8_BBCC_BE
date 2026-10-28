package com.bbcc.kidly.boundedcontext.product.in.replica;

import com.bbcc.kidly.boundedcontext.product.app.replica.ProductReplicaSellerService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 판매자 정보 변경됨을 받아 판매자 복사본 반영을 맡긴다.
 * 회원 도메인에 SellerEventListener 빈이 이미 있어서 이름 앞에 Product를 붙였다.
 */
@Component
@RequiredArgsConstructor
public class ProductSellerEventListener {

    private final ProductReplicaSellerService replicaSellerService;

    @DomainEventListener
    public void on(SellerChangedEvent event) {
        replicaSellerService.applySellerChanged(event);
    }
}
