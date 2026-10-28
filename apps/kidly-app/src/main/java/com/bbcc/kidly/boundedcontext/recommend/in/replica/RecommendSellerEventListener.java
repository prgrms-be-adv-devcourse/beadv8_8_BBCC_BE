package com.bbcc.kidly.boundedcontext.recommend.in.replica;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaSellerService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 판매자 정보 변경됨을 받아 판매자 복사본 반영을 맡긴다.
 * 회원에 {@code SellerEventListener}가 있어서, 스프링 빈 이름이 겹치지 않게 클래스 이름 앞에 도메인 이름을 붙인다.
 */
@Component
@RequiredArgsConstructor
public class RecommendSellerEventListener {

    private final RecommendReplicaSellerService replicaSellerService;

    @DomainEventListener
    public void on(SellerChangedEvent event) {
        replicaSellerService.applySellerChanged(event);
    }
}
