package com.bbcc.kidly.boundedcontext.recommend.in.replica;

import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildService;
import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 자녀 정보 변경됨을 받아 자녀 복사본 반영을 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class RecommendChildProfileEventListener {

    private final RecommendReplicaChildService replicaChildService;

    @DomainEventListener
    public void on(ChildChangedEvent event) {
        replicaChildService.applyChildChanged(event);
    }
}
