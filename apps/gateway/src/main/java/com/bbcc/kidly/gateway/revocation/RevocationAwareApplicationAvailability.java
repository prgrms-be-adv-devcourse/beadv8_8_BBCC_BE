package com.bbcc.kidly.gateway.revocation;

import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Component;

/**
 * 무효화 목록을 다 읽기 전에는 readiness 를 REFUSING_TRAFFIC 으로 본다(#1176). liveness 는 그대로다.
 * Boot 의 ApplicationAvailabilityBean 을 대신한다(Boot 는 이 빈이 있으면 자기 것을 만들지 않는다).
 * Boot 가 기동 끝에 ACCEPTING_TRAFFIC 을 알려도 덮이지 않도록 상태를 읽을 때 거른다.
 * k3s readinessProbe(/actuator/health/readiness)와 롤링 배포가 이 값을 본다.
 */
@Component
class RevocationAwareApplicationAvailability extends ApplicationAvailabilityBean {

    private final RevocationReplay replay;

    RevocationAwareApplicationAvailability(RevocationReplay replay) {
        this.replay = replay;
    }

    @Override
    public <S extends AvailabilityState> S getState(Class<S> stateType, S defaultState) {
        return refusing(stateType)
            ? stateType.cast(ReadinessState.REFUSING_TRAFFIC)
            : super.getState(stateType, defaultState);
    }

    @Override
    public <S extends AvailabilityState> S getState(Class<S> stateType) {
        return refusing(stateType) ? stateType.cast(ReadinessState.REFUSING_TRAFFIC) : super.getState(stateType);
    }

    private boolean refusing(Class<?> stateType) {
        return stateType == ReadinessState.class && !replay.caughtUp();
    }
}
